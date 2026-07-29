# bids4s

bids4s is a typed Scala 3 library for parsing, validating, querying, and
inspecting Brain Imaging Data Structure (BIDS) projects. Its domain APIs run on
the JVM and Scala.js. Local filesystem discovery is a JVM adapter.

[Read the executable guides](docs/README.md) for installation, querying,
validation, tables, and confounds.

## Quick start

Load a local project and ask for its raw functional scans:

```scala
import bids4s.*
import bids4s.io.*

import java.nio.file.Path

val loaded = BidsProjectLoader.loadStrict(Path.of("/data/study"))
val scans = loaded.map(_.funcScans())
```

`scans` is an `Either[BidsProjectLoadError, Vector[BidsFile]]`. Loading and
validation failures remain explicit, while a valid project exposes direct
methods for common work:

```scala
val selected = loaded.map { project =>
  (
    project.subjects(scope = BidsScope.Raw),
    project.funcScans(task = "rest"),
    project.anatScans(subid = "01"),
    project.eventFiles(subid = "01", task = "rest")
  )
}
```

`BidsScope.Raw` selects source files, `Derivatives` selects processed outputs,
and `All` selects both.

The selector strings in these convenience methods are glob patterns. Plain
values match exactly, `*` matches any text, and `?` matches one character. Use
`BidsQuery.from` when a query needs regular expressions or mixed policies.

## Parsing and custom queries

Parsing a BIDS filename does not require filesystem access:

```scala
val name = BidsName.parse("sub-01_task-rest_run-1_bold.nii.gz")
```

For an exact entity query, pass the domain keys and values directly:

```scala
val query = BidsQuery.exact(
  Vector(
    EntityKey.Subject -> "01",
    EntityKey.Task -> "rest"
  ),
  scope = BidsScope.Raw
)
```

`BidsName.parse` and `BidsQuery.exact` return `Either[BidsError, ...]`. A
single-filter query is shorter:

```scala
val subject01 = BidsQuery.exact(EntityKey.Subject, "01")
val firstRun = BidsQuery.run(1) // also matches run-01
```

Entity presence is direct too:

```scala
val withRuns = BidsQuery.present(EntityKey.Run)
val withoutRuns = BidsQuery.absent(EntityKey.Run)
```

These correspond to PyBIDS `Query.REQUIRED` and `Query.NONE` without using a
sentinel as an entity value. `EntityFilter.optional` is available when
composing several filter policies.

Use `BidsQuery.from` to combine entity filters and advanced matching policies.
Its `matchMode` controls entity values; its `filename` strings are regular
expressions. Use `BidsQuery.fromPatterns` with `QueryPattern.Exact`, `Glob`, or
`Regex` when the filename policy should be explicit.

## Loading and validation

Choose the loader according to what should happen when content is invalid:

- `load` preserves the permissive compatibility behavior.
- `loadChecked` returns a usable project together with every independently
  detectable content issue.
- `loadStrict` rejects structural errors without discarding the complete
  report.

Operational failures such as an inaccessible root remain separate from content
diagnostics. `BidsProjectLoaderF[F]` provides the same policies for Cats Effect
applications without choosing or running an effect runtime.

## Tables and confounds

Tables support direct column access and selection:

```scala
val duration = table.column("duration")
val timing = table.select("onset", "duration")
```

`column` returns `Either[BidsError, Vector[Option[String]]]`; `select` returns
`Either[BidsError, BidsTable]`. Both report an `InvalidTable` error when a
requested column is absent.

For fMRIPrep confounds, select a named set without manually transporting the
result of `ConfoundSets.named`:

```scala
val motion = BidsProjectLoader.readConfoundSet(
  project,
  name = "motion24",
  subid = "01",
  task = "rest"
)

val pca = BidsProjectLoader.readConfoundStrategy(
  project,
  ConfoundStrategy.PcaBasic80,
  subid = "01",
  task = "rest"
)
```

Both calls return
`Either[BidsError, Vector[BidsConfoundSelectionFile]]`. Each result records the
requested and resolved columns, the selected table, and cleaning diagnostics.

## Main APIs

- Filenames and entities: `BidsName`, `BidsEntities`, `BidsDatatypeSpec`, and
  `BidsRegistry`.
- Projects and selection: `BidsManifest`, `BidsProject`, and `BidsQuery`.
- Diagnostics: `BidsIssue`, `BidsIssueReport`, and `BidsValidationReport`.
- Metadata and tables: `BidsUri`, `BidsJson`, `BidsTable`, and `BidsEvents`.
- fMRIPrep confounds: `ConfoundSets`, `ConfoundStrategy`, and
  `ConfoundSelector`.
- Effectful loading: `BidsProjectLoaderF[F]`.

## Dependency

The intended `0.1` JVM coordinates are:

```scala
libraryDependencies += "io.github.canardlapin" %% "bids4s" % version
```

Use `%%%` in a Scala.js or cross-project build:

```scala
libraryDependencies += "io.github.canardlapin" %%% "bids4s" % version
```

No released version is implied by the current snapshot version.

## Development

The publication floor is Scala 3.3.8 so the initial artifact remains consumable
by the oldest live downstream compiler. Source compatibility is also checked
against newer supported Scala 3 compilers.

Run the full JVM and Scala.js gate:

```sh
sbt compileAll testAll
```

Compile the API reference and executable guide site:

```sh
sbt coreJVM/doc docs/tlSite
```

The JVM coverage gate requires at least 80% statement and 70% branch coverage:

```sh
sbt coverageJVM
```

PyBIDS behavior and matched performance workloads are tracked in
[docs/pybids-parity.md](docs/pybids-parity.md). Run the local comparison with
`scripts/pybids-court.sh`.

Loading and validation behavior is described in
[docs/loading-and-validation.md](docs/loading-and-validation.md).
The supported compiler and dependency policy is in
[docs/compatibility.md](docs/compatibility.md).

## Provenance and license

bids4s was extracted from the BIDS module developed in
[canardlapin/scalafim](https://github.com/canardlapin/scalafim). See
[PROVENANCE.md](PROVENANCE.md) for the exact source boundary.

Licensed under the [Apache License 2.0](LICENSE).
