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

## Explain inherited metadata

`project.resolveMetadata(path)` returns effective JSON, ordered contributing
documents, the final document supplying each top-level field, and field history.
Applications that already have an inventory can reuse an immutable
`BidsMetadataResolver` without constructing a filesystem loader.

```scala mdoc:compile-only
import bids4s.*

def explain(project: BidsProject, path: BidsPath) =
  project.resolveMetadata(path).map { resolved =>
    (
      resolved.metadata,
      resolved.datasetRoot,
      resolved.sources,
      resolved.origins.get("RepetitionTime"),
      resolved.history("RepetitionTime")
    )
  }
```

The resolver indexes candidates by directory and suffix once. It limits
inheritance to the deepest declared derivative root or inventoried nested
`dataset_description.json` boundary. All paths are project-relative; a
`datasetRoot` of `None` means the project dataset root.

Multiple applicable JSON documents in one directory produce
`BidsError.AmbiguousMetadata`; an applicable inventoried document with no
decoded content produces `BidsError.MissingMetadata`. Checked loaders retain
these failures as ordered content diagnostics, strict loaders reject them, and
permissive loading still returns the project. Metadata queries on ambiguous
content return the typed failure.

### Inheritance corrections

Inherited JSON now follows top-level key replacement: a lower-level object
value replaces the entire value for that key. The general-purpose
`JsonValue.merge` helper retains its existing recursive behavior.
Root-dataset sidecars no longer contribute to nested derivative datasets.
Ambiguous same-directory sidecars are no longer resolved by specificity or
lexicographic order. These are semantic corrections in the pre-release API;
existing valid, unambiguous scalar inheritance keeps its result.

This API explains JSON inheritance only. Non-JSON nearest-file associations,
schema-aware relationship resolution, complete metadata placement validation,
and filesystem preservation are separate contracts.

## Explicit fieldmap relationships

`BidsFieldmapResolver` interprets explicit declarations on NIfTI images. It is a
pure, portable operation over a manifest and metadata resolver:

```scala mdoc:compile-only
import bids4s.*

def declaredFieldmaps(project: BidsProject): BidsFieldmapReport =
  BidsFieldmapResolver.resolve(
    project.manifest,
    BidsMetadataResolver(project.manifest, project.sidecars, project.derivatives)
  )
```

For applications already computing inheritance in a batch, `fromResolved`
accepts the resulting `Map[BidsPath, BidsMetadataResolution]`. Callers retain
their own metadata-resolution diagnostics for targets absent from that map.

Each association identifies the target acquisition, all source images belonging
to one estimation group, its mechanism, an optional identifier, and the exact
metadata documents/fields providing the evidence. A shared `B0FieldIdentifier`
forms a group within one participant's dataset tree, including its sessions.
A `B0FieldSource` array retains separate group records. Self-reference is valid.
When both mechanisms are present, both are retained; this API does not rank
them or infer an algorithm choice.

Current-dataset `bids::` URIs resolve within the selected dataset boundary.
Deprecated participant-relative `IntendedFor` paths resolve with a warning.
Named external datasets need a pinned composition adapter and produce an
explicit unsupported finding. Missing identifiers/targets, malformed fields,
noncanonical paths, scope mismatch, and metadata failures remain inspectable.
Use `report.issues.exists(_.isError)` to distinguish an incomplete/invalid
report from a successful empty graph. Results and evidence order deterministically.

This bounded profile allows at most 1024 values per declaration field, 4096
characters per value, 100000 declaration references, and 100000 expanded
source members. Exceeding a budget produces a blocking finding; partial
relationships must not be treated as a complete graph.

This API does not infer unnamed fieldmaps or phase/magnitude companions,
qualify geometry or distortion-correction suitability, select a processing
algorithm, or implement DWI/event companion resolution. Existing loaders keep
their prior behavior; the caller explicitly invokes this new assessment.

Semantics follow the explicit declaration sections of the
[BIDS MRI specification, version 1.11.2](https://bids-specification.readthedocs.io/en/v1.11.2/modality-specific-files/magnetic-resonance-imaging-data.html#expressing-the-mr-protocol-intent-for-fieldmaps).
