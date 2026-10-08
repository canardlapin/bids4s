package bids4s

enum BidsFieldmapMechanism:
  case B0FieldSource, IntendedFor

final case class BidsAssociationEvidence(artifact: BidsPath, document: BidsPath, field: String)

/** All sources in one identifier group jointly describe an estimation instance.
  * Separate associations retain separate identifiers/mechanisms; they are not ranked alternatives.
  */
final case class BidsFieldmapAssociation(
    target: BidsPath,
    sources: Vector[BidsPath],
    mechanism: BidsFieldmapMechanism,
    identifier: Option[String],
    evidence: Vector[BidsAssociationEvidence]
)

enum BidsFieldmapIssueCode:
  case MetadataResolution, InvalidDeclaration, InvalidParticipantScope, MissingIdentifier
  case InvalidReference, MissingTarget, UnsupportedTarget, UnsupportedExternalReference, DatasetBoundary, LegacyIntendedFor, LimitExceeded

final case class BidsFieldmapIssue(
    code: BidsFieldmapIssueCode, path: BidsPath, field: Option[String], message: String
):
  def isError: Boolean = code != BidsFieldmapIssueCode.LegacyIntendedFor

final case class BidsFieldmapReport(
    associations: Vector[BidsFieldmapAssociation], issues: Vector[BidsFieldmapIssue]
)

/** Pure interpretation of explicit NIfTI fieldmap declarations, using already-resolved metadata.
  * Does not infer unnamed fieldmaps, acquisition proximity, image geometry, or algorithm suitability.
  * Current-dataset BIDS URIs and deprecated participant-relative IntendedFor paths are supported.
  */
object BidsFieldmapResolver:
  val Version: String = "bids-fieldmap-associations-v1"

  def resolve(manifest: BidsManifest, metadata: BidsMetadataResolver): BidsFieldmapReport =
    val images = manifest.files.filter(f =>
      BidsName.parseGeneric(f.fileName).toOption.exists(n => n.extension == "nii" || n.extension == "nii.gz"))
      .distinctBy(_.path).sortBy(_.path.value)
    val outcomes = images.map(f => f.path -> metadata.resolve(f.path))
    val resolved = outcomes.collect { case (path, Right(value)) => path -> value }.toMap
    val errors = outcomes.collect { case (path, Left(error)) =>
      BidsFieldmapIssue(BidsFieldmapIssueCode.MetadataResolution, path, None, error.message)
    }
    fromResolved(manifest, resolved, errors)

  /** Batch seam: callers which already computed inheritance need not resolve it again. */
  def fromResolved(
      manifest: BidsManifest,
      metadata: Map[BidsPath, BidsMetadataResolution],
      initialIssues: Vector[BidsFieldmapIssue] = Vector.empty
  ): BidsFieldmapReport =
    val issues = Vector.newBuilder[BidsFieldmapIssue]
    val _ = issues ++= initialIssues
    val associations = Vector.newBuilder[BidsFieldmapAssociation]
    val manifestPaths = manifest.files.map(_.path).toSet
    val imagePaths = manifest.files.filter(f =>
      BidsName.parseGeneric(f.fileName).toOption.exists(n => n.extension == "nii" || n.extension == "nii.gz"))
      .map(_.path).toSet
    val images = metadata.valuesIterator.filter(m => imagePaths(m.target)).toVector.sortBy(_.target.value)

    def issue(code: BidsFieldmapIssueCode, m: BidsMetadataResolution, field: String, message: String): Unit =
      val _ = issues += BidsFieldmapIssue(code, m.target, Some(field), message)

    def strings(m: BidsMetadataResolution, field: String): Vector[String] =
      m.metadata.fields.get(field) match
        case None => Vector.empty
        case Some(value) =>
          val values = value match
            case JsonValue.Str(v) => Some(Vector(v))
            case JsonValue.Arr(vs) if vs.nonEmpty && vs.forall(_.isInstanceOf[JsonValue.Str]) =>
              Some(vs.collect { case JsonValue.Str(v) => v })
            case _ => None
          values match
            case Some(vs) if vs.size <= 1024 && vs.forall(v => v.trim.nonEmpty && v.length <= 4096 && !v.exists(_.isControl)) =>
              vs.distinct.sorted
            case _ =>
              issue(BidsFieldmapIssueCode.InvalidDeclaration, m, field,
                "expected a nonempty string or array of strings, at most 1024 entries and 4096 characters per entry")
              Vector.empty

    def evidence(m: BidsMetadataResolution, field: String): Vector[BidsAssociationEvidence] =
      m.origins.get(field).toVector.map(BidsAssociationEvidence(m.target, _, field))

    // The participant folder immediately below the selected dataset boundary establishes scope.
    def participant(m: BidsMetadataResolution): Option[String] =
      val local = m.datasetRoot.fold(m.target.value)(r => m.target.value.stripPrefix(r.value + "/"))
      val folder = local.takeWhile(_ != '/')
      val label = BidsName.parseGeneric(m.target.fileName).toOption.flatMap(_.entities.get(EntityKey.Subject))
      label.filter(s => folder == "sub-" + s).map(_ => folder)

    val declarations = images.map { m =>
      val identifiers = strings(m, "B0FieldIdentifier")
      val sources = strings(m, "B0FieldSource")
      val targets = strings(m, "IntendedFor")
      val subject = participant(m)
      if (identifiers.nonEmpty || sources.nonEmpty || targets.exists(!_.startsWith("bids:"))) && subject.isEmpty then
        issue(BidsFieldmapIssueCode.InvalidParticipantScope, m, "B0FieldIdentifier/B0FieldSource/IntendedFor",
          "declaration requires a matching participant folder within its dataset")
      (m, subject, identifiers, sources, targets)
    }
    if declarations.map((_, _, identifiers, sources, targets) => identifiers.size.toLong + sources.size + targets.size).sum > 100000 then
      return BidsFieldmapReport(Vector.empty, issues.result() :+ BidsFieldmapIssue(
        BidsFieldmapIssueCode.LimitExceeded, images.head.target, None, "fieldmap declaration budget exceeds 100000 references"))
    var remaining = 100000
    def reserve(m: BidsMetadataResolution, field: String, count: Int): Boolean =
      if count <= remaining then
        remaining -= count
        true
      else
        issue(BidsFieldmapIssueCode.LimitExceeded, m, field, "expanded relationship budget exceeds 100000 source members")
        false
    val providers = declarations.flatMap { (m, subject, identifiers, _, _) =>
      subject.toVector.flatMap(s => identifiers.map(id => (m.datasetRoot, s, id) -> m))
    }.groupMap(_._1)(_._2)

    declarations.foreach { (m, subject, _, sources, targets) =>
      subject.foreach { s =>
        sources.foreach { identifier =>
          val group = providers.getOrElse((m.datasetRoot, s, identifier), Vector.empty).sortBy(_.target.value)
          if group.isEmpty then issue(BidsFieldmapIssueCode.MissingIdentifier, m, "B0FieldSource",
            s"no image in this participant's dataset tree defines '$identifier'")
          else if reserve(m, "B0FieldSource", group.size) then
            val proof = (evidence(m, "B0FieldSource") ++ group.flatMap(evidence(_, "B0FieldIdentifier")))
              .distinct.sortBy(e => (e.artifact.value, e.document.value, e.field))
            val _ = associations += BidsFieldmapAssociation(m.target, group.map(_.target),
              BidsFieldmapMechanism.B0FieldSource, Some(identifier), proof)
        }
      }
      targets.foreach { reference =>
        def canonical(value: String): Option[BidsPath] =
          BidsPath.relative(value).toOption.filter(p => p.value == value && !value.contains('\\') &&
            !value.contains(':') && !value.exists(_.isControl))
        val relative =
          if reference.startsWith("bids:") then
            BidsUri.parse(reference) match
              case Right(uri) if !uri.datasetName.isCurrent =>
                issue(BidsFieldmapIssueCode.UnsupportedExternalReference, m, "IntendedFor",
                  "external dataset references require an explicitly pinned dataset composition")
                None
              case Right(uri) => canonical(uri.relativePath.value).filter(_.value == reference.stripPrefix("bids::"))
                .orElse {
                  issue(BidsFieldmapIssueCode.InvalidReference, m, "IntendedFor", "reference must use a canonical relative path")
                  None
                }
              case Left(error) =>
                issue(BidsFieldmapIssueCode.InvalidReference, m, "IntendedFor", error.message)
                None
          else
            canonical(reference) match
              case Some(p) =>
                issue(BidsFieldmapIssueCode.LegacyIntendedFor, m, "IntendedFor",
                  "participant-relative IntendedFor is deprecated; prefer a current-dataset BIDS URI")
                subject.map(s => BidsPath(s + "/" + p.value))
              case None =>
                issue(BidsFieldmapIssueCode.InvalidReference, m, "IntendedFor", "unsafe or noncanonical reference")
                None
        relative.foreach { local =>
          val target = m.datasetRoot.fold(local)(r => BidsPath(r.value + "/" + local.value))
          metadata.get(target).filter(_ => imagePaths(target)) match
            case None if manifestPaths(target) && !imagePaths(target) =>
              issue(BidsFieldmapIssueCode.UnsupportedTarget, m, "IntendedFor", "target exists but is outside this NIfTI relationship profile")
            case None => issue(BidsFieldmapIssueCode.MissingTarget, m, "IntendedFor", s"target image is absent or unresolved: ${target.value}")
            case Some(other) if other.datasetRoot != m.datasetRoot =>
              issue(BidsFieldmapIssueCode.DatasetBoundary, m, "IntendedFor", "reference crosses an independent dataset boundary")
            case Some(_) if reserve(m, "IntendedFor", 1) =>
              val _ = associations += BidsFieldmapAssociation(target, Vector(m.target),
                BidsFieldmapMechanism.IntendedFor, None, evidence(m, "IntendedFor"))
            case Some(_) => ()
        }
      }
    }
    BidsFieldmapReport(
      associations.result().distinct.sortBy(a => (a.target.value, a.mechanism.ordinal, a.identifier.getOrElse(""), a.sources.map(_.value).mkString("\n"))),
      issues.result().distinct.sortBy(i => (i.path.value, i.field.getOrElse(""), i.code.ordinal, i.message))
    )
