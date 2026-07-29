# Getting started

This guide adds bids4s to an sbt build, loads a local BIDS project, and selects
its raw functional scans.

## Add the dependency

The JVM coordinates are:

```scala
libraryDependencies += "io.github.canardlapin" %% "bids4s" % "@VERSION@"
```

Use `%%%` in a Scala.js or cross-project build:

```scala
libraryDependencies += "io.github.canardlapin" %%% "bids4s" % "@VERSION@"
```

The current version is a snapshot, not a published release. To try it from this
checkout, run `sbt coreJVM/publishLocal` and use the displayed snapshot version
in another local build.

## Load a local project

Filesystem loading is available on the JVM:

```scala mdoc:compile-only
import bids4s.*
import bids4s.io.*

import java.nio.file.Path

val loaded: Either[BidsProjectLoadError, BidsProject] =
  BidsProjectLoader.loadStrict(Path.of("/data/study"))

val scans: Either[BidsProjectLoadError, Vector[BidsFile]] =
  loaded.map(_.funcScans())
```

`loadStrict` separates two failure classes:

- `BidsProjectLoadError.Operation` reports filesystem and parsing operations
  that could not complete.
- `BidsProjectLoadError.Validation` contains the complete, deterministically
  ordered issue report when the loaded content has structural errors.

See [Loading and validation](loading-and-validation.md) when an application
needs a usable project together with its issues.

## Select common files

Once loaded, a project has direct methods for common tasks:

```scala mdoc:compile-only
import bids4s.*

def selectCommon(project: BidsProject) =
  (
    project.subjects(scope = BidsScope.Raw),
    project.funcScans(task = "rest"),
    project.anatScans(subid = "01"),
    project.eventFiles(subid = "01", task = "rest")
  )
```

Selector strings are glob patterns. A plain value matches exactly, `*` matches
any text, and `?` matches one character.

`BidsScope` controls which dataset roots are searched:

- `Raw` selects source files.
- `Derivatives` selects processed outputs.
- `All` selects both.

Continue with [Querying projects](querying.md) for entity presence, regular
expressions, and named derivative pipelines.
