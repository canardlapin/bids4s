#!/usr/bin/env python3
"""Create the deterministic filesystem fixture used by both benchmark courts."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


DEFAULT_TASKS = ("rest", "nback", "stroop")


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def touch(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.touch()


def create_fixture(
    root: Path,
    subjects: int,
    sessions: int,
    runs: int,
    tasks: tuple[str, ...],
) -> list[str]:
    if subjects < 1 or sessions < 1 or runs < 1 or not tasks:
        raise ValueError("subjects, sessions, runs, and tasks must all be non-empty")
    if root.exists() and any(root.iterdir()):
        raise ValueError(f"fixture root must not already contain files: {root}")

    root.mkdir(parents=True, exist_ok=True)
    write_text(
        root / "dataset_description.json",
        json.dumps(
            {
                "Name": "bids4s PyBIDS court",
                "BIDSVersion": "1.10.1",
                "DatasetType": "raw",
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
    )
    write_text(
        root / "participants.tsv",
        "participant_id\n"
        + "".join(f"sub-{subject:03d}\n" for subject in range(1, subjects + 1)),
    )

    for task_index, task in enumerate(tasks):
        write_text(
            root / f"task-{task}_bold.json",
            json.dumps(
                {
                    "EchoTime": 0.03 + task_index * 0.001,
                    "RepetitionTime": 0.8 + task_index * 0.1,
                    "TaskName": task,
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
        )

    derivative = root / "derivatives" / "fmriprep"
    write_text(
        derivative / "dataset_description.json",
        json.dumps(
            {
                "Name": "bids4s PyBIDS court derivatives",
                "BIDSVersion": "1.10.1",
                "DatasetType": "derivative",
                "GeneratedBy": [{"Name": "fMRIPrep", "Version": "25.1.4"}],
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
    )
    for task in tasks:
        write_text(
            derivative / f"task-{task}_space-MNI152NLin2009cAsym_desc-preproc_bold.json",
            json.dumps(
                {
                    "SkullStripped": True,
                    "SpatialReference": "MNI152NLin2009cAsym",
                    "TaskName": task,
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
        )

    for subject in range(1, subjects + 1):
        subject_id = f"{subject:03d}"
        for session in range(1, sessions + 1):
            session_id = f"{session:02d}"
            prefix = f"sub-{subject_id}_ses-{session_id}"

            touch(root / f"sub-{subject_id}" / f"ses-{session_id}" / "anat" / f"{prefix}_T1w.nii.gz")

            for task in tasks:
                for run in range(1, runs + 1):
                    run_id = f"{run:02d}"
                    scan = f"{prefix}_task-{task}_run-{run_id}"

                    touch(root / f"sub-{subject_id}" / f"ses-{session_id}" / "func" / f"{scan}_bold.nii.gz")
                    write_text(
                        root / f"sub-{subject_id}" / f"ses-{session_id}" / "func" / f"{scan}_events.tsv",
                        "onset\tduration\ttrial_type\n0\t1\tcourt\n",
                    )

                    derivative_scan = (
                        f"{scan}_space-MNI152NLin2009cAsym_desc-preproc_bold.nii.gz"
                    )
                    derivative_confounds = f"{scan}_desc-confounds_timeseries.tsv"
                    derivative_func = (
                        derivative
                        / f"sub-{subject_id}"
                        / f"ses-{session_id}"
                        / "func"
                    )
                    touch(derivative_func / derivative_scan)
                    write_text(
                        derivative_func / derivative_confounds,
                        "trans_x\ttrans_y\ttrans_z\n0\t0\t0\n",
                    )

    return sorted(
        path.relative_to(root).as_posix()
        for path in root.rglob("*")
        if path.is_file()
    )


def checksum(paths: list[str]) -> str:
    return hashlib.sha256("\n".join(paths).encode("utf-8")).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--subjects", type=int, default=64)
    parser.add_argument("--sessions", type=int, default=2)
    parser.add_argument("--runs", type=int, default=4)
    parser.add_argument("--tasks", nargs="+", default=list(DEFAULT_TASKS))
    args = parser.parse_args()

    paths = create_fixture(
        args.root.resolve(),
        subjects=args.subjects,
        sessions=args.sessions,
        runs=args.runs,
        tasks=tuple(args.tasks),
    )
    print(
        json.dumps(
            {
                "files": len(paths),
                "path_checksum": checksum(paths),
                "runs": args.runs,
                "sessions": args.sessions,
                "subjects": args.subjects,
                "tasks": args.tasks,
            },
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
