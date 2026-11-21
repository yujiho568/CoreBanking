"""Save ONLY the five public preview rows of DATOP DSET000004, never label them a full download."""
import csv
from datetime import datetime,timezone
import hashlib
from html.parser import HTMLParser
import json
from pathlib import Path
import re
from urllib.request import urlopen

URL='https://www.datop.or.kr/data/set/dataSetDetail.do?dataSetId=DSET000004'
EXPECTED=['기준연월','(비식별)취급은행대표코드','(비식별)개설은행대표코드','자금구분코드','거래금액']
COLUMNS=['BASE_YM','HNDE_BANK_RPTV_CODE','OPENBANK_RPTV_CODE','FND_TPCD','TRAN_AMT']
class Preview(HTMLParser):
    def __init__(self):
        super().__init__();self.active=False;self.done=False;self.cell=None;self.row=[];self.rows=[];self.headers=[]
    def handle_starttag(self,tag,attrs):
        if tag=='table' and dict(attrs).get('id')=='dataSetSampleMgmt' and not self.done:self.active=True
        if self.active and tag in ('th','td'):self.cell=[]
        if self.active and tag=='tr':self.row=[]
    def handle_data(self,data):
        if self.active and self.cell is not None:self.cell.append(data)
    def handle_endtag(self,tag):
        if not self.active:return
        if tag in ('th','td') and self.cell is not None:
            value=''.join(self.cell).strip();self.cell=None
            if tag=='th':self.headers.append(value)
            else:self.row.append(value)
        if tag=='tr' and self.row:self.rows.append(self.row)
        if tag=='table':self.active=False;self.done=True

def save(folder):
    folder=Path(folder);folder.mkdir(parents=True,exist_ok=True)
    with urlopen(URL,timeout=20) as response:raw=response.read()
    parser=Preview();parser.feed(raw.decode('utf-8'))
    if parser.headers!=EXPECTED or not parser.rows or any(len(row)!=5 for row in parser.rows):
        raise ValueError('Public preview schema changed')
    html=folder/'DSET000004-preview-source.html';html.write_bytes(raw)
    csvpath=folder/'DSET000004-public-preview.csv'
    with csvpath.open('w',encoding='utf-8',newline='') as output:
        writer=csv.writer(output);writer.writerow(COLUMNS)
        for row in parser.rows:
            if not re.fullmatch(r'[0-9,]+',row[4]):raise ValueError('Unexpected preview amount')
            writer.writerow([*row[:4],row[4].replace(',','')])
    manifest={'dataset_id':'DSET000004','source_url':URL,'source_kind':'PUBLIC_PREVIEW','full_download':False,
        'fetched_at':datetime.now(timezone.utc).isoformat(),'row_count':len(parser.rows),'original_headers':parser.headers,
        'html_sha256':hashlib.sha256(raw).hexdigest(),'csv_sha256':hashlib.sha256(csvpath.read_bytes()).hexdigest()}
    (folder/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    return manifest

if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',default='datop-data/preview')
    args=parser.parse_args();print(json.dumps(save(args.output),ensure_ascii=True,indent=2))
