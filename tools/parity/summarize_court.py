#!/usr/bin/env python3
"""Validate the two courts and write a compact comparison summary."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


JMH_NAMES = {
    "index": "index",
    "metadata": "metadata",
    "queryRawBold": "query_raw_bold",
    "queryRunAbsent": "query_run_absent",
    "queryRunOne": "query_run_one",
    "queryRunPresent": "query_run_present",
    "querySubject": "query_subject",
    "querySubjectTask": "query_subject_task",
    "subjectIds": "subject_ids",
}


def load(path: Path) -> object:
    return json.loads(path.read_text(encoding="utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pybids", type=Path, required=True)
    parser.add_argument("--bids4s-validation", type=Path, required=True)
    parser.add_argument("--bids4s-jmh", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    pybids = load(args.pybids)
    bids4s = load(args.bids4s_validation)
    jmh = load(args.bids4s_jmh)

    py_validation = pybids["validation"]
    scala_validation = bids4s["validation"]
    cases = sorted(set(py_validation) | set(scala_validation))
    mismatches = [
        case
        for case in cases
        if py_validation.get(case) != scala_validation.get(case)
    ]
    if mismatches:
        details = "\n".join(
            f"{case}: PyBIDS={py_validation.get(case)!r}, bids4s={scala_validation.get(case)!r}"
            for case in mismatches
        )
        raise SystemExit(f"behavioral validation failed:\n{details}")

    scala_timings: dict[str, dict[str, float | None]] = {}
    for result in jmh:
        method = result["benchmark"].rsplit(".", 1)[-1]
        if method not in JMH_NAMES:
            continue
        allocation = (
            result.get("secondaryMetrics", {})
            .get("gc.alloc.rate.norm", {})
            .get("score")
        )
        scala_timings[JMH_NAMES[method]] = {
            "allocation_bytes": allocation,
            "median_ms": result["primaryMetric"]["score"],
        }

    py_timings = pybids["timings"]
    missing = sorted(set(py_timings) - set(scala_timings))
    if missing:
        raise SystemExit(f"JMH result omitted workloads: {', '.join(missing)}")

    rows = []
    for workload in sorted(py_timings):
        py_ms = float(py_timings[workload]["median_ms"])
        scala_ms = float(scala_timings[workload]["median_ms"])
        allocation = scala_timings[workload]["allocation_bytes"]
        speedup = py_ms / scala_ms
        allocation_text = "n/a" if allocation is None else f"{float(allocation):,.0f}"
        rows.append(
            f"| `{workload}` | {py_ms:.4f} | {scala_ms:.4f} | {speedup:.2f}x | {allocation_text} |"
        )

    args.output.write_text(
        "\n".join(
            [
                "# Local PyBIDS comparison",
                "",
                "All designated output counts, path checksums, subject IDs, and metadata values matched.",
                "The timings come from separate CPython and JVM processes, so they are directional",
                "cross-runtime evidence rather than a shared-process benchmark.",
                "",
                "| Workload | PyBIDS median ms | bids4s JMH ms | bids4s speedup | bids4s bytes/op |",
                "| --- | ---: | ---: | ---: | ---: |",
                *rows,
                "",
            ]
        ),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
