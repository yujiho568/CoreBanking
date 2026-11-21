"""Measure your CoreBanking endpoint. Do not point this tool at KFTC servers."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import math
import statistics
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit, urlunsplit, parse_qsl, urlencode
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("url", help="CoreBanking balance, transactions, or imported-dataset URL")
    parser.add_argument("--owner", required=True)
    parser.add_argument("--requests", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=1)
    parser.add_argument("--refresh", action="store_true", help="Bypass cached results; concurrent identical loads may still coalesce")
    parser.add_argument("--timeout", type=float, default=120)
    args = parser.parse_args()
    url = urlsplit(args.url)
    if url.scheme not in ("http", "https") or not url.hostname or "openbanking.or.kr" in url.hostname:
        parser.error("Use a CoreBanking server URL, never the KFTC API")
    if not (url.path.startswith("/api/external-accounts/") or url.path.startswith("/api/imported-datasets/")):
        parser.error("Use an external account or imported dataset query endpoint")
    if min(args.requests, args.concurrency, args.timeout) <= 0:
        parser.error("Requests, concurrency and timeout must be positive")
    query = [(k, v) for k, v in parse_qsl(url.query) if k != "refresh"]
    query.append(("refresh", str(args.refresh).lower()))
    target = urlunsplit(url._replace(query=urlencode(query)))

    def request(_):
        begin = time.perf_counter()
        try:
            with urlopen(Request(target, headers={"X-User-Id": args.owner}), timeout=args.timeout) as response:
                response.read()
                status = response.status
        except HTTPError as error:
            status = error.code
        except (URLError, TimeoutError, OSError):
            status = 0
        return (time.perf_counter() - begin) * 1000, status

    # Warm caches separately; excluded from measurements and report request count.
    warm_status = request(0)[1] if not args.refresh else None
    if warm_status is not None and not 200 <= warm_status < 300:
        parser.error(f"Warm-up failed with status {warm_status}")
    begin = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(request, range(args.requests)))
    elapsed = time.perf_counter() - begin
    latencies = sorted(ms for ms, _ in results)
    statuses = {}
    for _, status in results:
        statuses[str(status)] = statuses.get(str(status), 0) + 1
    errors = sum(not 200 <= status < 300 for _, status in results)
    print(json.dumps({"requests": args.requests, "concurrency": args.concurrency, "refresh": args.refresh,
        "mean_ms": statistics.mean(latencies), "p95_ms": latencies[math.ceil(len(latencies)*.95)-1],
        "throughput_per_second": len(results)/elapsed, "errors": errors, "error_rate": errors/len(results),
        "statuses": statuses}, indent=2))
    if errors:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
