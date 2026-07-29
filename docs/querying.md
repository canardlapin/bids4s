# Querying projects

Use direct `BidsProject` selectors for common scans and tables. Use `BidsQuery`
when selection depends on several entities, entity presence, filename patterns,
scope, or a named derivative pipeline.

The examples below use an in-memory manifest, so they run without a dataset on
disk:

```scala mdoc:silent
import bids4s.*

val manifest = BidsManifest.fromRelativePaths(
  Vector(
    "sub-01/func/sub-01_task-rest_run-01_bold.nii.gz",
    "sub-01/func/sub-01_task-rest_run-02_bold.nii.gz",
    "sub-02/func/sub-02_task-nback_bold.nii.gz",
    "sub-02/func/sub-02_task-nback_events.tsv"
  )
)
```

## Exact entity queries

`BidsQuery.exact` accepts typed entity keys and returns
`Either[BidsError, BidsQuery]`:

```scala mdoc
BidsQuery
  .exact(
    Vector(
      EntityKey.Subject -> "01",
      EntityKey.Task -> "rest"
    ),
    scope = BidsScope.Raw
  )
  .map(query => manifest.paths(query).map(_.value))
```

Exact run queries normalize padding, so `BidsQuery.run(1)` also matches
`run-01`:

```scala mdoc
BidsQuery
  .run(1)
  .map(query => manifest.paths(query).map(_.value))
```

## Required and absent entities

Presence queries do not use sentinel strings as entity values:

```scala mdoc
(
  manifest.paths(BidsQuery.present(EntityKey.Run)).map(_.value),
  manifest.paths(BidsQuery.absent(EntityKey.Run)).map(_.value)
)
```

These operations correspond to PyBIDS `Query.REQUIRED` and `Query.NONE`.
`EntityFilter.optional` is available when a larger query needs an optional
entity.

## Advanced entity matching

Construct filters first, then pass them to `BidsQuery.from`. Every invalid
filter or regular expression is rejected before the query can run:

```scala mdoc
for
  subject <- EntityFilter.from(EntityKey.Subject, "0[12]")
  query <- BidsQuery.from(
    filters = Vector(subject),
    matchMode = MatchMode.Regex,
    scope = BidsScope.Raw
  )
yield manifest.paths(query).map(_.value)
```

`matchMode` applies to entity values. The raw `filename` strings accepted by
`BidsQuery.from` are always regular expressions. When filename semantics should
be explicit, use `BidsQuery.fromPatterns`:

```scala mdoc
for
  pattern <- QueryPattern.glob("*_events.tsv")
  query <- BidsQuery.fromPatterns(
    filename = Vector(pattern),
    scope = BidsScope.Raw
  )
yield manifest.paths(query).map(_.value)
```

Use `pipeline = Some(PipelineName("fmriprep"))` with
`BidsScope.Derivatives` to restrict a query to one derivative root.
