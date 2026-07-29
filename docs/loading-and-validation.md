# Loading and validation

bids4s keeps filesystem failures separate from BIDS content diagnostics. Choose
the loader method according to whether invalid content should remain usable.

## Loader policies

| Method | Result | Use it when |
| --- | --- | --- |
| `load` | `Either[BidsError, BidsProject]` | Existing code needs the permissive compatibility behavior. |
| `loadChecked` | `Either[BidsError, BidsValidationReport[BidsProject]]` | The application should inspect every detectable issue and may still use the project. |
| `loadStrict` | `Either[BidsProjectLoadError, BidsProject]` | Structural errors must reject the project, but the complete issue report must be retained. |

The signatures compile as ordinary public API calls:

```scala mdoc:compile-only
import bids4s.*
import bids4s.io.*

import java.nio.file.Path

val root = Path.of("/data/study")

val permissive: Either[BidsError, BidsProject] =
  BidsProjectLoader.load(root)

val checked: Either[BidsError, BidsValidationReport[BidsProject]] =
  BidsProjectLoader.loadChecked(root)

val strict: Either[BidsProjectLoadError, BidsProject] =
  BidsProjectLoader.loadStrict(root)
```

## Inspect a checked result

`BidsValidationReport` contains the usable value, all ordered issues, and
convenience views for errors and warnings:

```scala mdoc:compile-only
import bids4s.*

def summarize(report: BidsValidationReport[BidsProject]) =
  (
    report.value,
    report.errors,
    report.warnings,
    report.hasErrors
  )
```

`report.enforce(BidsValidationPolicy.Strict)` converts a checked report into an
`Either[BidsIssueReport, BidsValidationReport[BidsProject]]`. The issue report
is non-empty by construction and keeps deterministic diagnostic ordering.

## Operational failures

`loadChecked` returns `Left(BidsError.Io(...))` when discovery cannot complete,
for example because the root does not exist or a file cannot be read. Content
issues such as an invalid sidecar, missing required metadata, or inconsistent
entities belong in `BidsValidationReport`.

`loadStrict` wraps the same distinction:

- `BidsProjectLoadError.Operation(error)` contains the operational
  `BidsError`.
- `BidsProjectLoadError.Validation(report)` contains content diagnostics.

## Effectful applications

`BidsProjectLoaderF[F]` provides `load`, `loadChecked`, and `loadStrict` at the
JVM boundary for Cats Effect applications. It returns the same domain result
types inside `F`; it does not choose a runtime or call `unsafeRun*`.

The portable parser, validation, query, metadata, table, and confound APIs do
not require Cats Effect and remain available to Scala.js.
