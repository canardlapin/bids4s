# bids4s

bids4s is a typed Scala 3 library for parsing, validating, querying, and
inspecting Brain Imaging Data Structure (BIDS) projects. Its domain APIs run on
the JVM and Scala.js. Local filesystem discovery is a JVM adapter.

```scala
import bids4s.*

val name = BidsName.parse("sub-01_task-rest_run-1_bold.nii.gz")
val query =
  for
    subject <- EntityFilter.from(EntityKey.Subject, "01")
    result <- BidsQuery.from(
      filters = Vector(subject),
      matchMode = MatchMode.Exact,
      scope = BidsScope.Raw
    )
  yield result
```

On the JVM, load a local project with:

```scala
import bids4s.io.*

val project = BidsProjectLoader.loadStrict(
  java.nio.file.Path.of("/data/study")
)
```

`load` retains the permissive compatibility behavior. `loadChecked` returns a
usable project together with every independently detectable content issue.
`loadStrict` rejects structural errors without discarding the complete report.
Operational failures remain separate from content diagnostics.

## Main APIs

- `BidsName`, `BidsEntities`, `BidsDatatypeSpec`, and `BidsRegistry` parse and
  describe typed BIDS filenames.
- `BidsManifest`, `BidsProject`, and `BidsQuery` provide immutable project
  discovery and selection.
- `BidsIssue`, `BidsIssueReport`, and `BidsValidationReport` preserve ordered,
  path-aware diagnostics.
- `BidsUri`, `BidsJson`, `BidsTable`, and `BidsEvents` cover BIDS references,
  metadata, TSV tables, and events.
- `ConfoundSets`, `ConfoundStrategy`, and `ConfoundSelector` select and reduce
  fMRIPrep confounds.
- `BidsProjectLoaderF[F]` suspends blocking JVM I/O, manages directory streams,
  and bounds independent sidecar reads without selecting an effect runtime.

## Dependency

The intended `0.1` coordinates are:

```scala
libraryDependencies += "io.github.canardlapin" %%% "bids4s" % version
```

No released version is implied by the current snapshot version.

## Development

The publication floor is Scala 3.3.8 so the initial artifact remains consumable
by the oldest live downstream compiler. Source compatibility is also checked
against newer supported Scala 3 compilers.

Run the full JVM and Scala.js court:

```sh
sbt compileAll testAll
```

The validation and effect boundary is described in
[docs/design/validation-and-effect-boundary.md](docs/design/validation-and-effect-boundary.md).
The supported compiler and dependency policy is in
[docs/compatibility.md](docs/compatibility.md).

## Provenance and license

bids4s was extracted from the BIDS module developed in
[canardlapin/scalafim](https://github.com/canardlapin/scalafim). See
[PROVENANCE.md](PROVENANCE.md) for the exact source boundary.

Licensed under the [Apache License 2.0](LICENSE).
