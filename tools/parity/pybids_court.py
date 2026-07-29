#!/usr/bin/env -S uv run --python 3.12 --script
# /// script
# requires-python = ">=3.10,<3.14"
# dependencies = ["pybids==0.22.0"]
# ///
"""Measure PyBIDS on the shared bids4s parity fixture."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import timeit
from pathlib import Path
from typing import Callable

import bids
from bids import BIDSLayout
from bids.layout import Query


def relative_paths(root: Path, paths: list[str]) -> list[str]:
    return sorted(Path(path).resolve().relative_to(root).as_posix() for path in paths)


def checksum(paths: list[str]) -> str:
    return hashlib.sha256("\n".join(paths).encode("utf-8")).hexdigest()


def timed_ms(operation: Callable[[], object], samples: int, iterations: int) -> dict[str, object]:
    values = [
        elapsed * 1000.0 / iterations
        for elapsed in timeit.Timer(operation).repeat(repeat=samples, number=iterations)
    ]
    return {
        "iterations_per_sample": iterations,
        "median_ms": statistics.median(values),
        "samples_ms": values,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--samples", type=int, default=7)
    parser.add_argument("--query-iterations", type=int, default=100)
    parser.add_argument("--id-iterations", type=int, default=1)
    parser.add_argument("--index-samples", type=int, default=5)
    args = parser.parse_args()

    root = args.root.resolve()
    layout = BIDSLayout(root, validate=False, derivatives=True)
    subject = "001"
    task = "rest"
    target = root / "sub-001" / "ses-01" / "func" / "sub-001_ses-01_task-rest_run-01_bold.nii.gz"

    all_files = relative_paths(root, layout.get(return_type="file", scope="all"))
    raw_bold = relative_paths(
        root,
        layout.get(
            return_type="file",
            scope="raw",
            suffix="bold",
            extension=".nii.gz",
        ),
    )
    derivative_bold = relative_paths(
        root,
        layout.get(
            return_type="file",
            scope="derivatives",
            suffix="bold",
            extension=".nii.gz",
            desc="preproc",
        ),
    )
    subject_files = relative_paths(
        root,
        layout.get(return_type="file", scope="all", subject=subject),
    )
    subject_task_files = relative_paths(
        root,
        layout.get(
            return_type="file",
            scope="all",
            subject=subject,
            task=task,
        ),
    )
    run_present_files = relative_paths(
        root,
        layout.get(return_type="file", scope="all", run=Query.REQUIRED),
    )
    run_absent_files = relative_paths(
        root,
        layout.get(return_type="file", scope="all", run=Query.NONE),
    )
    run_one_files = relative_paths(
        root,
        layout.get(return_type="file", scope="all", run=1),
    )
    subject_ids = layout.get(return_type="id", target="subject", scope="all")
    metadata = layout.get_metadata(target)

    validation = {
        "all_files": {"count": len(all_files), "checksum": checksum(all_files)},
        "derivative_bold": {
            "count": len(derivative_bold),
            "checksum": checksum(derivative_bold),
        },
        "raw_bold": {"count": len(raw_bold), "checksum": checksum(raw_bold)},
        "run_absent_files": {
            "count": len(run_absent_files),
            "checksum": checksum(run_absent_files),
        },
        "run_one_files": {
            "count": len(run_one_files),
            "checksum": checksum(run_one_files),
        },
        "run_present_files": {
            "count": len(run_present_files),
            "checksum": checksum(run_present_files),
        },
        "subject_files": {
            "count": len(subject_files),
            "checksum": checksum(subject_files),
        },
        "subject_ids": {
            "count": len(subject_ids),
            "checksum": checksum(sorted(str(value) for value in subject_ids)),
        },
        "subject_task_files": {
            "count": len(subject_task_files),
            "checksum": checksum(subject_task_files),
        },
        "target_metadata": {
            "RepetitionTime": metadata.get("RepetitionTime"),
            "TaskName": metadata.get("TaskName"),
        },
    }

    timings = {
        "index": timed_ms(
            lambda: BIDSLayout(root, validate=False, derivatives=True),
            samples=args.index_samples,
            iterations=1,
        ),
        "metadata": timed_ms(
            lambda: layout.get_metadata(target),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_raw_bold": timed_ms(
            lambda: layout.get(
                return_type="file",
                scope="raw",
                suffix="bold",
                extension=".nii.gz",
            ),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_run_absent": timed_ms(
            lambda: layout.get(
                return_type="file",
                scope="all",
                run=Query.NONE,
            ),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_run_one": timed_ms(
            lambda: layout.get(
                return_type="file",
                scope="all",
                run=1,
            ),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_run_present": timed_ms(
            lambda: layout.get(
                return_type="file",
                scope="all",
                run=Query.REQUIRED,
            ),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_subject": timed_ms(
            lambda: layout.get(return_type="file", scope="all", subject=subject),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "query_subject_task": timed_ms(
            lambda: layout.get(
                return_type="file",
                scope="all",
                subject=subject,
                task=task,
            ),
            samples=args.samples,
            iterations=args.query_iterations,
        ),
        "subject_ids": timed_ms(
            lambda: layout.get(return_type="id", target="subject", scope="all"),
            samples=args.samples,
            iterations=args.id_iterations,
        ),
    }

    print(
        json.dumps(
            {
                "implementation": "PyBIDS",
                "python_version": __import__("platform").python_version(),
                "pybids_version": bids.__version__,
                "timings": timings,
                "validation": validation,
            },
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
