"""Local-only KFTC-shaped simulator backed by synthetic SQLite transactions. No real credentials."""
import argparse
from contextlib import closing
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import secrets
import sqlite3
import threading
import time
from urllib.parse import parse_qs, urlencode, urlsplit

CLIENT_ID = "mock-client"
CLIENT_SECRET = "mock-secret"
CALLBACK = "http://localhost:8082/api/open-banking/oauth/callback"
ACCOUNTS = ["999999999999999999999901", "999999999999999999999902"]


def seed(database, rows):
    database = Path(database)
    database.parent.mkdir(parents=True, exist_ok=True)
    with closing(sqlite3.connect(database)) as db:
        db.executescript("""
        CREATE TABLE IF NOT EXISTS metadata (version INTEGER PRIMARY KEY, rows INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS transactions (
            id INTEGER PRIMARY KEY, account TEXT NOT NULL, occurred_at TEXT NOT NULL,
            direction TEXT NOT NULL, amount INTEGER NOT NULL, balance INTEGER NOT NULL);
        CREATE INDEX IF NOT EXISTS ix_account_time_id ON transactions(account, occurred_at, id);
        """)
        existing = db.execute("SELECT rows FROM metadata WHERE version=1").fetchone()
        if existing:
            if existing[0] != rows:
                raise ValueError("Existing mock DB has a different row count; choose a new database path")
            return
        balances = {account: 1000000 for account in ACCOUNTS}
        start = datetime(2026, 11, 1, 9)
        records = []
        for i in range(rows):
            account = ACCOUNTS[i % 2]
            direction = "I" if (i // 2) % 2 == 0 else "O"
            amount = 10000 if direction == "I" else 5000
            balances[account] += amount if direction == "I" else -amount
            records.append((i + 1, account, (start + timedelta(minutes=i // 2)).isoformat(), direction, amount, balances[account]))
        db.executemany("INSERT INTO transactions VALUES (?,?,?,?,?,?)", records)
        db.execute("INSERT INTO metadata VALUES (1,?)", (rows,))
        db.commit()


class MockServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, database, rows=1000, delay_ms=0, failure="none"):
        seed(database, rows)
        self.database = str(database)
        self.delay_ms = delay_ms
        self.failure = failure
        self.lock = threading.Lock()
        self.codes = {}
        self.access = {}
        self.refresh = {}
        self.calls = {}
        super().__init__(address, Handler)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass  # Never log codes, token bodies, authorization headers or query strings.

    def send_json(self, value, status=200):
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def error(self, code, message, status=200):
        self.send_json({"rsp_code": code, "rsp_message": message}, status)

    def track(self, path):
        with self.server.lock:
            self.server.calls[path] = self.server.calls.get(path, 0) + 1

    def issue_tokens(self):
        access, refresh = "mock-access-" + secrets.token_hex(16), "mock-refresh-" + secrets.token_hex(16)
        with self.server.lock:
            self.server.access[access] = time.monotonic() + 3600
            self.server.refresh[refresh] = True
        return {"access_token": access, "refresh_token": refresh, "token_type": "Bearer", "expires_in": 3600,
                "scope": "login inquiry", "user_seq_no": "mock-user"}

    def do_POST(self):
        if urlsplit(self.path).path != "/oauth/2.0/token":
            return self.error("NOT_FOUND", "Unknown mock endpoint", 404)
        self.track("/oauth/2.0/token")
        length = int(self.headers.get("Content-Length", "0"))
        if not 0 < length <= 8192:
            return self.error("INVALID_REQUEST", "Invalid form size", 400)
        form = parse_qs(self.rfile.read(length).decode("utf-8"))
        get = lambda key: form.get(key, [""])[0]
        if get("client_id") != CLIENT_ID or get("client_secret") != CLIENT_SECRET:
            return self.error("INVALID_CLIENT", "Only mock credentials are accepted", 401)
        if get("grant_type") == "authorization_code":
            if get("redirect_uri") != CALLBACK:
                return self.error("INVALID_REDIRECT", "Unexpected callback", 400)
            with self.server.lock:
                expiry = self.server.codes.pop(get("code"), 0)
            if expiry <= time.monotonic():
                return self.error("INVALID_GRANT", "Mock code expired or consumed", 400)
        elif get("grant_type") == "refresh_token":
            with self.server.lock:
                valid = self.server.refresh.pop(get("refresh_token"), False)
            if not valid:
                return self.error("INVALID_GRANT", "Invalid mock refresh token", 400)
        else:
            return self.error("INVALID_GRANT", "Unsupported grant", 400)
        self.send_json(self.issue_tokens())

    def do_GET(self):
        parsed = urlsplit(self.path)
        path, query = parsed.path, parse_qs(parsed.query)
        get = lambda key, default="": query.get(key, [default])[0]
        if path == "/health":
            return self.send_json({"status": "UP", "mode": "MOCK", "synthetic": True})
        if path == "/stats":
            with self.server.lock:
                counts = dict(self.server.calls)
            return self.send_json({"mode": "MOCK", "calls": counts})
        self.track(path)
        if path == "/oauth/2.0/authorize":
            if get("client_id") != CLIENT_ID or get("redirect_uri") != CALLBACK or get("response_type") != "code" or not get("state"):
                return self.error("INVALID_REQUEST", "Use mock-client and the localhost:8082 callback", 400)
            code = "mock-code-" + secrets.token_hex(16)
            with self.server.lock:
                self.server.codes[code] = time.monotonic() + 600
            self.send_response(302)
            self.send_header("Location", CALLBACK + "?" + urlencode({"code": code, "state": get("state")}))
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        supported = ["/v2.0/user/me", "/v2.0/account/balance/fin_num", "/v2.0/account/transaction_list/fin_num"]
        if path not in supported:
            return self.error("NOT_FOUND", "Unknown mock endpoint", 404)
        token = self.headers.get("Authorization", "").removeprefix("Bearer ")
        with self.server.lock:
            valid = self.server.access.get(token, 0) > time.monotonic()
        if not valid:
            return self.error("O0001", "Invalid mock access token", 401)
        if self.server.delay_ms and path != "/v2.0/user/me":
            time.sleep(self.server.delay_ms / 1000)
        if self.server.failure == "http500" and path != "/v2.0/user/me":
            return self.error("MOCK_ERROR", "Injected HTTP failure", 500)
        if self.server.failure == "api-error" and path != "/v2.0/user/me":
            return self.error("A0002", "Injected mock business failure")
        common = {"rsp_code": "A0000", "rsp_message": "", "api_tran_id": "mock-" + secrets.token_hex(8)}
        if path == "/v2.0/user/me":
            if get("user_seq_no") != "mock-user":
                return self.error("INVALID_USER", "Unknown mock user", 400)
            return self.send_json({**common, "user_seq_no": "mock-user", "res_list": [
                {"fintech_use_num": a, "bank_code_std": "999", "bank_name": "Mock Bank", "account_alias": "Synthetic account " + str(i + 1),
                 "account_num_masked": "MOCK-***-" + str(i + 1), "inquiry_agree_yn": "Y"} for i, a in enumerate(ACCOUNTS)]})
        account = get("fintech_use_num")
        if account not in ACCOUNTS or len(get("bank_tran_id")) != 20:
            return self.error("A0001", "Invalid mock account or bank transaction ID", 400)
        with closing(sqlite3.connect(self.server.database)) as db:
            if path == "/v2.0/account/balance/fin_num":
                row = db.execute("SELECT balance,occurred_at FROM transactions WHERE account=? ORDER BY occurred_at DESC,id DESC LIMIT 1", (account,)).fetchone()
                balance = row[0] if row else 1000000
                return self.send_json({**common, "bank_rsp_code": "000", "bank_name": "Mock Bank", "balance_amt": str(balance),
                    "available_amt": str(balance), "account_type": "1", "product_name": "Synthetic deposit", "last_tran_date": row[1][:10].replace("-", "") if row else ""})
            try:
                start = datetime.strptime(get("from_date") + get("from_time", "000000"), "%Y%m%d%H%M%S")
                until = datetime.strptime(get("to_date") + get("to_time", "235959"), "%Y%m%d%H%M%S")
                offset = int(get("befor_inquiry_trace_info", "0"))
                if offset < 0 or start > until or get("inquiry_type") not in ("A", "I", "O") or get("sort_order") not in ("A", "D"):
                    raise ValueError()
            except ValueError:
                return self.error("A0001", "Invalid mock transaction query", 400)
            order = "DESC" if get("sort_order") == "D" else "ASC"
            sql = "SELECT id,occurred_at,direction,amount,balance FROM transactions WHERE account=? AND occurred_at>=? AND occurred_at<=?"
            params = [account, start.isoformat(), until.isoformat()]
            if get("inquiry_type") != "A":
                sql += " AND direction=?"
                params.append(get("inquiry_type"))
            sql += f" ORDER BY occurred_at {order},id {order} LIMIT 26 OFFSET ?"
            found = db.execute(sql, [*params, offset]).fetchall()
        items = [{"tran_date": datetime.fromisoformat(row[1]).strftime("%Y%m%d"), "tran_time": datetime.fromisoformat(row[1]).strftime("%H%M%S"),
            "inout_type": "입금" if row[2] == "I" else "출금", "tran_type": "대체", "print_content": "Synthetic transaction " + str(row[0]),
            "tran_amt": str(row[3]), "after_balance_amt": str(row[4]), "branch_name": "Mock branch"} for row in found[:25]]
        more = len(found) > 25
        self.send_json({**common, "bank_rsp_code": "000", "page_record_cnt": str(len(items)), "next_page_yn": "Y" if more else "N",
                        "befor_inquiry_trace_info": str(offset + len(items)) if more else "", "res_list": items})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=9099)
    parser.add_argument("--database", type=Path, default=Path("build/mock-demo/provider.sqlite"))
    parser.add_argument("--rows", type=int, default=1000)
    parser.add_argument("--delay-ms", type=int, default=0)
    parser.add_argument("--failure", choices=["none", "http500", "api-error"], default="none")
    args = parser.parse_args()
    if args.rows < 2 or not 0 <= args.delay_ms <= 10000 or not 0 <= args.port <= 65535:
        parser.error("Invalid rows, delay or port")
    server = MockServer(("127.0.0.1", args.port), args.database, args.rows, args.delay_ms, args.failure)
    print(json.dumps({"mode": "MOCK", "synthetic_rows": args.rows, "port": server.server_port}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
