package bids4s

/** One applicable authored document, in dataset-root-to-file order. */
final case class BidsMetadataSource(path: BidsPath, metadata: JsonValue.Obj)

/** A value supplied for a top-level metadata key. */
final case class BidsMetadataValue(source: BidsPath, value: JsonValue)

/** Effective JSON metadata and the evidence explaining each top-level key. */
final case class BidsMetadataResolution private[bids4s] (
    target: BidsPath,
    datasetRoot: Option[BidsPath],
    metadata: JsonValue.Obj,
    sources: Vector[BidsMetadataSource],
    origins: Map[String, BidsPath]
):
  def history(field: String): Vector[BidsMetadataValue] =
    sources.flatMap(source => source.metadata.fields.get(field).map(BidsMetadataValue(source.path, _)))

/** Immutable index for repeated, pure JSON inheritance queries over a manifest.
  *
  * Paths and derivative roots are project-relative. None denotes the project
  * dataset root; nested dataset_description.json entries and declared derivative
  * roots establish independent inheritance boundaries.
  */
final class BidsMetadataResolver private (
    byDirectoryAndKind: Map[(String, String), Vector[BidsMetadataResolver.Candidate]],
    datasetRoots: Vector[BidsPath]
):
  def resolve(path: BidsPath): Either[BidsError, BidsMetadataResolution] =
    for
      target <- BidsPath.relative(path.value)
      name <- BidsName.parse(target.fileName).orElse(BidsName.parseGeneric(target.fileName))
      resolution <- resolveWithinDataset(target, name)
    yield resolution

  private def resolveWithinDataset(target: BidsPath, name: BidsName): Either[BidsError, BidsMetadataResolution] =
    val datasetRoot = datasetRoots.filter(target.startsWithPath).maxByOption(_.value.length)
    val directory = target.parent.map(_.value).getOrElse("")
    val parts = directory.split('/').toVector.filter(_.nonEmpty)
    val ancestors = Vector("") ++ parts.indices.map(i => parts.take(i + 1).mkString("/")).toVector
    val localAncestors = datasetRoot.fold(ancestors)(root => ancestors.dropWhile(_ != root.value))

    val resolved = localAncestors.foldLeft[Either[BidsError, Vector[BidsMetadataSource]]](Right(Vector.empty)) {
      (acc, ancestor) =>
        acc.flatMap { sources =>
          val candidates = byDirectoryAndKind.getOrElse(ancestor -> name.kind, Vector.empty)
            .filter(candidate => applies(candidate.name, name))
          candidates match
            case Vector() => Right(sources)
            case Vector(candidate) =>
              candidate.metadata
                .toRight(BidsError.MissingMetadata(candidate.path))
                .map(metadata => sources :+ BidsMetadataSource(candidate.path, metadata))
            case multiple =>
              Left(BidsError.AmbiguousMetadata(target, multiple.map(_.path)))
        }
    }

    resolved.map { sources =>
      val fields = sources.foldLeft(Map.empty[String, JsonValue])((acc, source) => acc ++ source.metadata.fields)
      val origins = sources.foldLeft(Map.empty[String, BidsPath]) { (acc, source) =>
        acc ++ source.metadata.fields.keysIterator.map(_ -> source.path)
      }
      BidsMetadataResolution(target, datasetRoot, JsonValue.Obj(fields), sources, origins)
    }

  private def applies(candidate: BidsName, target: BidsName): Boolean =
    candidate.entities.keys.forall { key =>
      target.entities.get(key).contains(candidate.entities(key))
    }

object BidsMetadataResolver:
  private final case class Candidate(path: BidsPath, name: BidsName, metadata: Option[JsonValue.Obj])

  def apply(
      manifest: BidsManifest,
      sidecars: Map[BidsPath, JsonValue.Obj],
      derivatives: Vector[DerivativeRoot] = Vector.empty
  ): BidsMetadataResolver =
    val candidates = manifest.files
      .filter(_.fileName.endsWith(".json"))
      .distinctBy(_.path)
      .flatMap { file =>
        BidsName.parseGeneric(file.fileName).toOption.map(name => Candidate(file.path, name, sidecars.get(file.path)))
      }
      .sortBy(_.path.value)
      .groupBy(candidate => candidate.path.parent.map(_.value).getOrElse("") -> candidate.name.kind)

    val roots = (
      derivatives.map(_.root) ++
        manifest.files.filter(_.fileName == "dataset_description.json").flatMap(_.path.parent)
    ).distinct.sortBy(_.value)

    new BidsMetadataResolver(candidates, roots)
