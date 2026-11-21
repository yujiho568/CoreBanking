"""Explicit CSV mapping only. Inspect the dataset schema before supplying column names."""
import argparse
import csv
from datetime import datetime
from decimal import Decimal
import json
from pathlib import Path
import os
import tempfile


def normalize(source, destination, mapping):
    if source.resolve() == destination.resolve():
        raise ValueError("Source and normalized destination must differ")
    if destination.exists():
        raise ValueError("Destination already exists; choose a new path")
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(dir=destination.parent, suffix=".tmp")
    count = 0
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as output:
            writer = csv.writer(output)
            writer.writerow(["account_ref", "occurred_at", "amount", "direction", "description"])
            with source.open(encoding=mapping["encoding"], newline="") as original:
                reader = csv.DictReader(original, delimiter=mapping.get("delimiter", ","))
                required = [mapping[key] for key in ("account_column", "time_column", "amount_column", "direction_column", "description_column")]
                if not reader.fieldnames or not set(required).issubset(reader.fieldnames):
                    raise ValueError("Mapping columns are absent from the source header")
                incoming = set(mapping["incoming_values"])
                outgoing = set(mapping["outgoing_values"])
                if incoming & outgoing:
                    raise ValueError("Direction mappings overlap")
                for count, row in enumerate(reader, 1):
                    try:
                        if None in row or any(value is None for value in row.values()):
                            raise ValueError("Malformed source row")
                        direction = row[mapping["direction_column"]]
                        if direction not in incoming | outgoing:
                            raise ValueError("Unknown direction")
                        amount = Decimal(row[mapping["amount_column"]])
                        if not amount.is_finite() or amount < 0 or amount.as_tuple().exponent < -2:
                            raise ValueError("Amount must be nonnegative, with at most two decimals")
                        timestamp = datetime.strptime(row[mapping["time_column"]], mapping["timestamp_format"])
                        if timestamp.tzinfo is not None:
                            raise ValueError("Convert timestamps to the documented local time zone first")
                        account = row[mapping["account_column"]]
                        description = row[mapping["description_column"]]
                        if not account.strip() or len(account) > 128 or len(description) > 500:
                            raise ValueError("Invalid account reference or description length")
                        writer.writerow([account, timestamp.isoformat(), str(amount), "I" if direction in incoming else "O", description])
                    except Exception as failure:
                        raise ValueError(f"Invalid source row {count}; check the mapping and source schema") from failure
        os.replace(temporary, destination)
        return count
    finally:
        Path(temporary).unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--mapping", required=True, type=Path)
    args = parser.parse_args()
    count = normalize(args.source, args.destination, json.loads(args.mapping.read_text(encoding="utf-8")))
    print(json.dumps({"normalized_rows": count}))


if __name__ == "__main__":
    main()
