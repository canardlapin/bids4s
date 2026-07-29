# Tables and confounds

bids4s parses BIDS TSV content into an immutable `BidsTable`. Column lookup,
numeric conversion, and selection return typed errors when the requested data
is invalid.

## Parse and select columns

```scala mdoc:silent
import bids4s.*

val events =
  BidsTable.parse(
    """onset	duration	trial_type
      |0.0	1.5	target
      |2.0	0.5	control
      |""".stripMargin
  )

val timing =
  events.flatMap(_.select("onset", "duration"))
```

```scala mdoc
timing.map(table => (table.columns, table.nrows))
```

`BidsTable.parse` and `select` return `Either[BidsError, BidsTable]`.
`table.column("duration")` returns
`Either[BidsError, Vector[Option[String]]]`; missing TSV cells remain `None`.
An absent column produces `BidsError.InvalidTable`.

Use `columnNamed` when the column name should travel with its values, then call
`numeric` for checked finite-number conversion:

```scala mdoc
for
  table <- events
  column <- table.columnNamed("duration")
  values <- column.numeric
yield values
```

## Read tables from a project

The JVM loader reads project-relative tables and preserves their source paths:

```scala mdoc:compile-only
import bids4s.*
import bids4s.io.*

def eventTables(
    project: BidsProject
): Either[BidsError, Vector[BidsEventTableFile]] =
  BidsProjectLoader.readValidatedEventTableFiles(project)
```

Use `readEventTableFiles` for generic tables or
`readValidatedEventTableFiles` when `onset` and `duration` must satisfy the
events-table contract.

## Select fMRIPrep confounds

Named confound sets hide the intermediate lookup while keeping failures typed:

```scala mdoc:compile-only
import bids4s.*
import bids4s.io.*

def motionConfounds(
    project: BidsProject
): Either[BidsError, Vector[BidsConfoundSelectionFile]] =
  BidsProjectLoader.readConfoundSet(
    project,
    name = "motion24",
    subid = "01",
    task = "rest"
  )
```

For PCA-based strategies, use the closed `ConfoundStrategy` values:

```scala mdoc:compile-only
import bids4s.*
import bids4s.io.*

def pcaConfounds(
    project: BidsProject
): Either[BidsError, Vector[BidsConfoundSelectionFile]] =
  BidsProjectLoader.readConfoundStrategy(
    project,
    ConfoundStrategy.PcaBasic80,
    subid = "01",
    task = "rest"
  )
```

Each `BidsConfoundSelectionFile` records the requested and resolved columns, the
selected table, cleaning diagnostics, and optional PCA receipt. Unknown named
sets, absent columns, invalid numeric values, and invalid strategy parameters
return `BidsError` values.
