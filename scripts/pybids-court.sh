#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mode="${2:-quick}"
receipt="${1:-$repo_root/target/pybids-court-$mode}"

case "$mode" in
  quick)
    fixture_args=(--subjects 4 --sessions 2 --runs 2)
    pybids_args=(--samples 3 --query-iterations 20 --id-iterations 5 --index-samples 2)
    jmh_args="-i 3 -wi 2 -r 500ms -w 500ms"
    ;;
  full)
    fixture_args=(--subjects 64 --sessions 2 --runs 4)
    pybids_args=(--samples 7 --query-iterations 100 --id-iterations 1 --index-samples 5)
    jmh_args="-i 5 -wi 3 -r 1s -w 1s"
    ;;
  *)
    echo "mode must be 'quick' or 'full': $mode" >&2
    exit 2
    ;;
esac

if [[ -e "$receipt" ]] && [[ -n "$(find "$receipt" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "receipt directory must be absent or empty: $receipt" >&2
  exit 2
fi

mkdir -p "$receipt"
receipt="$(cd "$receipt" && pwd)"
fixture="$receipt/fixture"

{
  printf 'git.sha=%s\n' "$(git -C "$repo_root" rev-parse HEAD)"
  if [[ -n "$(git -C "$repo_root" status --porcelain=v1)" ]]; then
    printf 'git.dirty=true\n'
  else
    printf 'git.dirty=false\n'
  fi
  printf 'mode=%s\n' "$mode"
  printf 'os=%s\n' "$(uname -a)"
  printf 'python=%s\n' "$(python3 --version)"
  java -version 2>&1
} > "$receipt/environment.txt"
git -C "$repo_root" status --porcelain=v1 > "$receipt/git-status.txt"

python3 "$repo_root/tools/parity/make_fixture.py" "$fixture" "${fixture_args[@]}" > "$receipt/fixture.json"
uv run --python 3.12 "$repo_root/tools/parity/pybids_court.py" "$fixture" "${pybids_args[@]}" > "$receipt/pybids.json"

(
  cd "$repo_root"
  sbt --error "benchmarks/runMain bids4s.benchmarks.BidsLayoutCourt $fixture"
) > "$receipt/bids4s-validation.json"

(
  cd "$repo_root"
  sbt --error \
    "benchmarks/Jmh/run $jmh_args -f 1 -t 1 -prof gc -rf json -rff $receipt/bids4s-jmh.json -jvmArgsAppend -Dbids4s.benchmark.root=$fixture bids4s.benchmarks.BidsLayoutBenchmarks"
)

python3 "$repo_root/tools/parity/summarize_court.py" \
  --pybids "$receipt/pybids.json" \
  --bids4s-validation "$receipt/bids4s-validation.json" \
  --bids4s-jmh "$receipt/bids4s-jmh.json" \
  --output "$receipt/summary.md"

printf 'PyBIDS court complete: %s\n' "$receipt/summary.md"
