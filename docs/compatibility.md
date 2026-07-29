# Compatibility

## Scala and platforms

bids4s is configured to publish one Scala 3 artifact line:

```text
io.github.canardlapin:bids4s_3
io.github.canardlapin:bids4s_sjs1_3
```

The initial artifact will be compiled with Scala 3.3.8, the oldest compiler
used by a live downstream project. Source CI also compiles and tests the
library with Scala 3.4.2 and 3.7.4. The project will not publish separate
artifacts for Scala 3 minor versions because they share the `_3` suffix.

The supported runtime court is:

- JVM on Eclipse Temurin JDK 21;
- Scala.js with Node.js 24 and CommonJS output;
- sbt 1.10.5 for ordinary development.

## Public API

Version `0.1.x` follows early semantic versioning. Before the first stable
release, source-compatible additions are preferred, but necessary corrections
to invalid or misleading APIs may still be made with migration notes.

The domain result types are the public compatibility boundary. Cats validation
types are implementation details. Cats Effect appears in the explicit
`BidsProjectLoaderF[F]` JVM adapter and does not select or run an effect
runtime.

## Dependency versions

The extraction baseline retains Cats Core 2.12.0 and Cats Effect 3.5.4. A
dependency upgrade is a separate change and must pass the full JVM, Scala.js,
and staged-consumer court.
