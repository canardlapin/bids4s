# bids4s

bids4s parses, validates, and queries Brain Imaging Data Structure (BIDS)
projects from Scala 3. Use it when a Scala application needs BIDS filenames,
entities, manifests, metadata, tables, or fMRIPrep confounds without reducing
domain choices to unvalidated strings.

The portable domain API runs on the JVM and Scala.js. Local filesystem
discovery is a JVM adapter.

## Parse a BIDS filename

Parsing does not touch the filesystem:

```scala mdoc:silent
import bids4s.*

val parsed = BidsName.parse("sub-01_task-rest_run-1_bold.nii.gz")
```

```scala mdoc
parsed.map { name =>
  (
    name.entities.get(EntityKey.Subject),
    name.entities.get(EntityKey.Task),
    name.kind,
    name.extension
  )
}
```

`BidsName.parse` returns `Either[BidsError, BidsName]`. An invalid filename is a
typed `BidsError`; parsing does not return `null` or throw for expected input
errors.

## Choose the next guide

- [Getting started](getting-started.md) installs the library and loads a local
  project.
- [Querying projects](querying.md) covers direct selectors and custom queries.
- [Loading and validation](loading-and-validation.md) explains the three loader
  policies and their error types.
- [Tables and confounds](tables-and-confounds.md) reads tabular data and selects
  fMRIPrep confounds.
- [Compatibility](compatibility.md) records the supported Scala, JVM, and
  Scala.js versions.

The library is currently a `0.1.0-SNAPSHOT`; these guides do not imply that a
release has been published.
