# API reference

The generated Scaladoc is the reference for public symbols and method
signatures. Build it locally with:

```sh
sbt coreJVM/doc
```

The executable guides cover the main entry points:

- `BidsName`, `BidsEntities`, `BidsDatatypeSpec`, and `BidsRegistry` parse and
  classify filenames.
- `BidsManifest`, `BidsProject`, and `BidsQuery` discover and select files.
- `BidsIssue`, `BidsIssueReport`, and `BidsValidationReport` describe content
  diagnostics.
- `BidsUri`, `BidsJson`, `BidsTable`, and `BidsEvents` handle metadata and
  tables.
- `ConfoundSets`, `ConfoundStrategy`, and `ConfoundSelector` select fMRIPrep
  confounds.
- `BidsProjectLoader` and `BidsProjectLoaderF[F]` provide JVM filesystem
  adapters.

The project does not yet advertise a hosted Scaladoc URL because no release has
been published. The site can add a permanent API link once the first artifact
is available.
