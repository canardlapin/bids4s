# PyBIDS parity and performance

PyBIDS is the behavioral reference for BIDS dataset discovery and querying.
bids4s aims to return the same files and metadata for the same supported query,
while replacing string switches and runtime exceptions with checked Scala
values.

Parity does not require copying the `BIDSLayout` API. For example, PyBIDS
accepts an arbitrary keyword as a filter and then applies an
`invalid_filters` policy. bids4s accepts an `EntityKey`, so a misspelled
standard entity does not need an equivalent runtime policy. The observable
file selection should still agree.

## Current comparison

| PyBIDS behavior | bids4s status | Intended contract |
| --- | --- | --- |
| Exact and multi-value entity filters | Supported | Match the same files, with checked construction |
| Regular-expression entity filters | Supported | Regexes are validated before the query can run |
| Raw, all-derivative, and named-pipeline scope | Supported | Use `BidsScope` plus an optional `PipelineName` |
| Sorted file results | Supported | Sort by project-relative path |
| Unique subject, session, task, and run IDs | Supported | Return sorted values through direct project methods |
| Inherited JSON metadata | Partial | Match inheritance precedence within the correct dataset root |
| Required, optional, present, and absent entities | Supported | Use typed presence filters rather than string sentinels |
| Padded run normalization such as `1` versus `01` | Supported | Preserve the readable exact-query API and canonicalize internally |
| Nearest-file, fieldmap, bval, and bvec lookup | Not yet matched | Add typed result records where ambiguity matters |
| Persistent layout index | Not implemented | Measure repeated-query needs before adding storage or caching |
| Layout dataframe export | Not implemented | Keep the core independent; consider an optional frame4s adapter |
| Variables and statistical-model APIs | Out of current scope | Treat as separate modules if demand and evidence justify them |

The table is a coverage ledger, not a compatibility claim. A row moves to
supported only when an executable oracle case fixes the inputs, outputs, and
ordering.

## Run the court

The local court creates one deterministic dataset and gives the same directory
to PyBIDS 0.22.0 and bids4s:

```sh
scripts/pybids-court.sh
```

The script writes its fixture, raw results, and summary under
`target/pybids-court-quick`. This quick mode uses four subjects and short
measurement windows for routine harness checks.

Use the full 64-subject, 6,281-file fixture for a comparison receipt:

```sh
scripts/pybids-court.sh target/pybids-court-2026-07-28 full
```

The script refuses to overwrite a non-empty receipt.

The behavioral gate compares exact file counts, path checksums, subject IDs,
entity presence and absence, and inherited metadata before it summarizes any
timing. The performance workloads cover initial indexing, raw BOLD selection,
one- and two-entity queries, present and absent entities, subject enumeration,
padded run selection, and metadata lookup.

PyBIDS runs in CPython through `timeit`; bids4s runs in a forked JVM through
JMH with normalized allocation measurements. These are separate-runtime
measurements. They can identify large, stable gaps, but a ratio is not release
evidence until the fixture, versions, validation output, raw measurements, and
machine environment are preserved together. Raw PyBIDS output records the
iteration count for each workload; deliberately slow operations use fewer
iterations per sample rather than a shorter sample series.

## frame4s boundary

frame4s is not needed for filename parsing, project inventory, scope filtering,
or metadata inheritance. Adding it to the core would increase the dependency
and execution model without improving those operations.

An optional integration becomes useful when bids4s needs typed, streaming TSV
ingestion or a dataframe result comparable to `BIDSLayout.to_df()`. That
adapter should live outside the portable domain core and should preserve BIDS
column semantics rather than expose frame4s machinery in basic project
queries.
