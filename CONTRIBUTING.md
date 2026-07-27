# Contributing

Open an issue before making a broad API or storage change. Small correctness,
documentation, and test improvements may proceed directly.

Before submitting a change:

1. Keep shared code free of JVM-only APIs.
2. Add shared tests for portable behavior and JVM tests for filesystem behavior.
3. Preserve deterministic diagnostic ordering.
4. Run `sbt compileAll testAll`.
5. Run `git diff --check`.

Do not add remote storage, caching, retry, streaming, or a hidden effect runtime
without a concrete use case and explicit compatibility review.
