import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.parse import urlencode, urlsplit, parse_qs
from urllib.request import Request, build_opener, HTTPRedirectHandler, urlopen
from urllib.error import HTTPError
from mock_openbanking import MockServer, CALLBACK, CLIENT_ID, CLIENT_SECRET, ACCOUNTS

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None

class MockTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.server = MockServer(('127.0.0.1',0),Path(self.temp.name)/'mock.sqlite',120)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = 'http://127.0.0.1:'+str(self.server.server_port)
    def tearDown(self):
        self.server.shutdown();self.server.server_close();self.thread.join();self.temp.cleanup()
    def request(self, path, form=None, token=None):
        headers = {'Authorization':'Bearer '+token} if token else {}
        data = urlencode(form).encode() if form is not None else None
        if data is not None: headers['Content-Type']='application/x-www-form-urlencoded'
        try:
            with urlopen(Request(self.base+path,data=data,headers=headers),timeout=5) as r:
                return r.status,json.load(r)
        except HTTPError as error:
            return error.code,json.load(error)
    def tokens(self):
        query = urlencode(dict(client_id=CLIENT_ID,redirect_uri=CALLBACK,response_type='code',state='test-state'))
        with self.assertRaises(HTTPError) as redirect:
            build_opener(NoRedirect).open(self.base+'/oauth/2.0/authorize?'+query)
        self.assertEqual(redirect.exception.code,302)
        callback = parse_qs(urlsplit(redirect.exception.headers['Location']).query)
        self.assertEqual(callback['state'],['test-state'])
        form = dict(client_id=CLIENT_ID,client_secret=CLIENT_SECRET,grant_type='authorization_code',code=callback['code'][0],redirect_uri=CALLBACK)
        status,tokens = self.request('/oauth/2.0/token',form)
        self.assertEqual(status,200)
        self.assertEqual(self.request('/oauth/2.0/token',form)[0],400)
        return tokens
    def test_oauth_refresh_and_unauthorized_access(self):
        self.assertEqual(self.request('/v2.0/user/me?user_seq_no=mock-user')[0],401)
        tokens = self.tokens()
        status,me = self.request('/v2.0/user/me?user_seq_no=mock-user',token=tokens['access_token'])
        self.assertEqual(status,200);self.assertEqual(len(me['res_list']),2)
        form = dict(client_id=CLIENT_ID,client_secret=CLIENT_SECRET,grant_type='refresh_token',refresh_token=tokens['refresh_token'])
        status,new = self.request('/oauth/2.0/token',form)
        self.assertEqual(status,200);self.assertNotEqual(new['access_token'],tokens['access_token'])
        self.assertEqual(self.request('/oauth/2.0/token',form)[0],400)
    def test_transaction_pages_filters_and_errors(self):
        token = self.tokens()['access_token']
        query = dict(fintech_use_num=ACCOUNTS[0],bank_tran_id='MOCK000001U123456789',from_date='20261101',to_date='20261121',inquiry_type='A',sort_order='D')
        path = '/v2.0/account/transaction_list/fin_num?'
        status,first = self.request(path+urlencode(query),token=token)
        self.assertEqual(status,200);self.assertEqual(len(first['res_list']),25);self.assertEqual(first['next_page_yn'],'Y')
        query['befor_inquiry_trace_info'] = first['befor_inquiry_trace_info']
        _,second = self.request(path+urlencode(query),token=token)
        self.assertNotEqual(first['res_list'][0]['print_content'],second['res_list'][0]['print_content'])
        query['befor_inquiry_trace_info']='0';query['inquiry_type']='I'
        _,incoming = self.request(path+urlencode(query),token=token)
        self.assertTrue(all(row['inout_type']=='입금' for row in incoming['res_list']))
        self.server.failure='api-error'
        self.assertEqual(self.request('/v2.0/user/me?user_seq_no=mock-user',token=token)[0],200)
        self.assertEqual(self.request(path+urlencode(query),token=token)[1]['rsp_code'],'A0002')
        self.server.failure='http500'
        self.assertEqual(self.request(path+urlencode(query),token=token)[0],500)
    def test_authorize_rejects_external_callback(self):
        query = urlencode(dict(client_id=CLIENT_ID,redirect_uri='https://example.com',response_type='code',state='state'))
        self.assertEqual(self.request('/oauth/2.0/authorize?'+query)[0],400)

if __name__=='__main__': unittest.main()
