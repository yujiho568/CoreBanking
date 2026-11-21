"""End-to-end verification against the local mock profile; never calls the real testbed."""
import json
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError

BASE='http://localhost:8082'
HEADERS={'X-User-Id':'mock-demo-user'}

def get(path, owner=None):
    headers=HEADERS if owner is None else {'X-User-Id':owner}
    try:
        with urlopen(Request(BASE+path,headers=headers),timeout=120) as response:
            return response.status,json.load(response)
    except HTTPError as failure:
        return failure.code,json.load(failure)

def count():
    status,data=get('/actuator/metrics/openbanking.provider.requests')
    if status==404:return 0
    assert status==200,data
    return int(sum(x['value'] for x in data['measurements'] if x['statistic']=='COUNT'))

def main():
    status,connection=get('/api/mock/connect')
    assert status==200 and len(connection['accounts'])==2,connection
    status,accounts=get('/api/external-accounts')
    assert status==200 and len(accounts)==2,accounts
    account=accounts[0]['externalAccountId']
    path='/api/external-accounts/'+account
    results=[]
    def check(name,suffix,expected_calls):
        before=count();status,data=get(path+suffix);delta=count()-before
        assert status==200 and delta==expected_calls,(name,status,delta,data)
        results.append({'case':name,'http_status':status,'provider_http_attempts':delta})
        return data
    fresh=check('balance_fresh','/balance?refresh=true',1)
    cached=check('balance_cached','/balance',0)
    assert fresh==cached
    refreshed=check('balance_refresh','/balance?refresh=true',1)
    assert refreshed['fetchedAt']!=fresh['fetchedAt']
    query='/transactions?fromDate=2026-11-01&toDate=2026-11-21&maxPages=3'
    fresh=check('transactions_three_pages',query+'&refresh=true',3)
    assert len(fresh['transactions'])==75 and fresh['pagesFetched']==3 and fresh['hasMore'],fresh
    cached=check('transactions_cached',query,0)
    assert cached==fresh
    one=check('transactions_changed_page_limit',query.replace('maxPages=3','maxPages=1'),1)
    assert len(one['transactions'])==25
    before=count()
    assert get(path+'/balance',owner='another-user')[0]==403
    assert get(path+'/transactions?fromDate=2026-11-01&toDate=2026-11-21&maxPages=21')[0]==400
    assert count()==before
    report={'mode':'MOCK','database':'H2 application + SQLite synthetic provider','cache':'ConcurrentMapCache, no TTL expiry',
            'connected_accounts':2,'transactions_verified':75,'results':results,'ownership_checked_before_cache':True,'invalid_page_limit_rejected':True}
    output=Path('build/mock-demo/verification.json');output.parent.mkdir(parents=True,exist_ok=True)
    output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
