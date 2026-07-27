# AGENTS.md

Guidance for coding agents working in **bids4s**, a typed Scala 3 library for
the Brain Imaging Data Structure (BIDS).

## Layout

- `modules/core/shared` contains the portable domain, parser, validation,
  query, table, metadata, and confound APIs.
- `modules/core/jvm` contains local filesystem adapters.
- `modules/first-contact` compiles outside the `bids4s` package and proves that
  the documented public API is sufficient.
- Shared code must remain JVM-independent and cross-compile to Scala.js.

## Build and test

- Baseline compiler: Scala 3.3.8.
- Build tool: sbt 1.10.5.
- Test framework: MUnit.
- Run `sbt compileAll testAll` before declaring work complete.
- Shared behavior must pass on both JVM and Scala.js.

## Design contract

- Scala 3 ADTs, opaque types, smart constructors, and `Either` define BIDS
  meaning and failures.
- Cats Core may accumulate independent validation defects behind domain-first
  result types.
- Cats Effect belongs only at the JVM loader/store boundary. Do not expose a
  runtime, call `unsafeRun*`, or move effects into shared domain values.
- Keep diagnostic ordering deterministic.
- Preserve `load`, `loadChecked`, and `loadStrict` compatibility unless a
  versioned migration explicitly changes them.
- Remote stores, writes, caching, retry, and streaming require separate
  evidence and are not implied by `BidsStore`.

## Scala style

- Use Scala 3 significant indentation with two spaces.
- Prefer closed enums and small immutable records.
- Keep invariant-bearing constructors private and expose checked factories.
- Represent public failures with typed values rather than `null` or thrown
  exceptions.
- Match the surrounding source style and keep diffs warning-clean.

## GitHub identity

- The canonical repository is `canardlapin/bids4s`.
- Use repo-local identity `canardlapin
  <307091466+canardlapin@users.noreply.github.com>`.
- Use `io.github.canardlapin` Maven coordinates.
- Do not change the machine-wide Git or GitHub CLI identity.
