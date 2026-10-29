"""Summarize JMeter CSV result files; no network/dependencies required."""
import csv, json, math, sys
from pathlib import Path

def summarize(path):
    with Path(path).open(encoding="utf-8-sig", newline="") as source:
        rows = list(csv.DictReader(source))
    if not rows:
        raise ValueError("No samples")
    elapsed = sorted(int(row["elapsed"]) for row in rows)
    start = min(int(row["timeStamp"]) for row in rows)
    end = max(int(row["timeStamp"]) + int(row["elapsed"]) for row in rows)
    def percentile(p):
        return elapsed[max(0, math.ceil(len(elapsed) * p) - 1)]
    return {"samples": len(rows), "meanMs": sum(elapsed) / len(rows),
            "p95Ms": percentile(.95), "p99Ms": percentile(.99),
            "errorPercent": 100 * sum(row["success"].lower() != "true" for row in rows) / len(rows),
            "observedThroughputPerSec": len(rows) * 1000 / (end - start) if end > start else None,
            "percentileMethod": "nearest-rank", "throughputWindow": "first start to last end"}

if __name__ == "__main__":
    print(json.dumps(summarize(sys.argv[1]), indent=2))
