# bids4s core

This cross-project contains the published `bids4s` artifact.

- `shared` owns portable BIDS names, entities, validation, manifests, queries,
  metadata, JSON, TSV tables, events, and confound selection.
- `jvm` owns local filesystem discovery and synchronous or Cats Effect loaders.
- `js` currently needs no platform-specific source.

The public packages are:

```scala
import bids4s.*
import bids4s.io.* // JVM only
```

Run both supported platforms from the repository root:

```sh
sbt coreJVM/test coreJS/test
```
