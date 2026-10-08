package bids4s

class BidsMetadataSuite extends munit.FunSuite:
  private val raw = BidsPath("sub-01/func/sub-01_task-rest_run-01_bold.nii.gz")

  private def value[A](result: Either[BidsError, A]): A =
    result.fold(error => fail(error.message), identity)

  private def obj(fields: (String, JsonValue)*): JsonValue.Obj =
    JsonValue.Obj(fields.toMap)

  private def project(
      target: BidsPath = raw,
      documents: Vector[(String, JsonValue.Obj)] = Vector.empty,
      extra: Vector[String] = Vector.empty,
      roots: Vector[DerivativeRoot] = Vector.empty
  ): BidsProject =
    BidsProject(
      root = BidsPath("/data/study"),
      description = None,
      participants = Vector.empty,
      derivatives = roots,
      manifest = BidsManifest.fromRelativePaths(Vector(target.value) ++ documents.map(_._1) ++ extra, roots),
      sidecars = documents.map { case (path, json) => BidsPath(path) -> json }.toMap
    )

  test("resolution explains inherited values, overrides, and their history"):
    val root = "task-rest_bold.json"
    val local = "sub-01/func/sub-01_task-rest_run-01_bold.json"
    val study = project(documents = Vector(
      root -> obj("TaskName" -> JsonValue.Str("Rest"), "RepetitionTime" -> JsonValue.Num(1.5)),
      local -> obj("RepetitionTime" -> JsonValue.Num(2.0))
    ))
    val resolved = value(study.resolveMetadata(raw))
    assertEquals(resolved.target, raw)
    assertEquals(resolved.datasetRoot, None)
    assertEquals(resolved.sources.map(_.path.value), Vector(root, local))
    assertEquals(resolved.metadata.fields("RepetitionTime"), JsonValue.Num(2.0))
    assertEquals(resolved.origins("TaskName"), BidsPath(root))
    assertEquals(resolved.origins("RepetitionTime"), BidsPath(local))
    assertEquals(resolved.history("RepetitionTime"), Vector(
      BidsMetadataValue(BidsPath(root), JsonValue.Num(1.5)),
      BidsMetadataValue(BidsPath(local), JsonValue.Num(2.0))
    ))
    assertEquals(resolved.history("Absent"), Vector.empty)
    assertEquals(value(study.metadata(raw)), resolved.metadata)

  test("lower-level object values replace the same top-level key"):
    val study = project(documents = Vector(
      "task-rest_bold.json" -> obj(
        "Column" -> obj("Description" -> JsonValue.Str("old"), "Units" -> JsonValue.Str("s")),
        "TaskName" -> JsonValue.Str("Rest")
      ),
      "sub-01/sub-01_task-rest_bold.json" -> obj(
        "Column" -> obj("Description" -> JsonValue.Str("new"))
      )
    ))
    val resolved = value(study.resolveMetadata(raw))
    assertEquals(resolved.metadata.fields("Column"), obj("Description" -> JsonValue.Str("new")))
    assertEquals(resolved.metadata.fields("TaskName"), JsonValue.Str("Rest"))

  test("JSON null is an explicit value with an origin"):
    val study = project(documents = Vector(
      "task-rest_bold.json" -> obj("Custom" -> JsonValue.Str("before")),
      "sub-01/sub-01_task-rest_bold.json" -> obj("Custom" -> JsonValue.Null)
    ))
    val resolved = value(study.resolveMetadata(raw))
    assertEquals(resolved.metadata.fields.get("Custom"), Some(JsonValue.Null))
    assertEquals(resolved.origins("Custom"), BidsPath("sub-01/sub-01_task-rest_bold.json"))

  test("raw sidecars do not leak into a declared derivative dataset"):
    val target = BidsPath("derivatives/custom/sub-01/func/sub-01_task-rest_desc-preproc_bold.nii.gz")
    val root = DerivativeRoot(BidsPath("derivatives/custom"), PipelineName("custom"))
    val study = project(target, Vector(
      "task-rest_bold.json" -> obj("RawOnly" -> JsonValue.Bool(true)),
      "derivatives/custom/task-rest_bold.json" -> obj("RepetitionTime" -> JsonValue.Num(2.0))
    ), roots = Vector(root))
    val resolved = value(study.resolveMetadata(target))
    assertEquals(resolved.datasetRoot, Some(root.root))
    assertEquals(resolved.metadata, obj("RepetitionTime" -> JsonValue.Num(2.0)))
    assertEquals(resolved.sources.map(_.path.value), Vector("derivatives/custom/task-rest_bold.json"))

  test("a derivative with no own sidecars has no raw inherited values"):
    val target = BidsPath("derivatives/custom/sub-01/func/sub-01_task-rest_bold.nii.gz")
    val study = project(target, Vector(
      "task-rest_bold.json" -> obj("RepetitionTime" -> JsonValue.Num(9.0))
    ), roots = Vector(DerivativeRoot(BidsPath("derivatives/custom"), PipelineName("custom"))))
    assertEquals(value(study.metadata(target)), JsonValue.EmptyObject)

  test("nested dataset descriptions establish inheritance boundaries independently of scope"):
    val target = BidsPath("collection/nested/sub-01/func/sub-01_task-rest_bold.nii.gz")
    val study = project(target, Vector(
      "task-rest_bold.json" -> obj("Outside" -> JsonValue.Bool(true)),
      "collection/task-rest_bold.json" -> obj("OutsideToo" -> JsonValue.Bool(true)),
      "collection/nested/task-rest_bold.json" -> obj("TaskName" -> JsonValue.Str("Nested"))
    ), extra = Vector("collection/nested/dataset_description.json"))
    val resolved = value(study.resolveMetadata(target))
    assertEquals(resolved.datasetRoot, Some(BidsPath("collection/nested")))
    assertEquals(resolved.metadata, obj("TaskName" -> JsonValue.Str("Nested")))

  test("the deepest declared root wins regardless of discovery order"):
    val target = BidsPath("derivatives/outer/derivatives/inner/sub-01/func/sub-01_task-rest_bold.nii.gz")
    val roots = Vector(
      DerivativeRoot(BidsPath("derivatives/outer"), PipelineName("outer")),
      DerivativeRoot(BidsPath("derivatives/outer/derivatives/inner"), PipelineName("inner"))
    )
    val study = project(target, Vector(
      "derivatives/outer/task-rest_bold.json" -> obj("Outside" -> JsonValue.Bool(true))
    ), roots = roots)
    assertEquals(value(study.resolveMetadata(target)).datasetRoot, Some(roots(1).root))
    assertEquals(study.resolveMetadata(target), study.copy(derivatives = roots.reverse).resolveMetadata(target))
    assertEquals(value(study.metadata(target)), JsonValue.EmptyObject)

  test("multiple applicable sidecars at one level are a deterministic typed conflict"):
    val study = project(documents = Vector(
      "task-rest_bold.json" -> obj("A" -> JsonValue.Num(1.0)),
      "bold.json" -> obj("B" -> JsonValue.Num(2.0))
    ))
    val expected = Left(BidsError.AmbiguousMetadata(raw, Vector(BidsPath("bold.json"), BidsPath("task-rest_bold.json"))))
    assertEquals(study.resolveMetadata(raw), expected)
    assertEquals(study.copy(manifest = BidsManifest(study.manifest.files.reverse)).resolveMetadata(raw), expected)

  test("an applicable inventoried sidecar without decoded content is not silently skipped"):
    val path = BidsPath("task-rest_bold.json")
    val study = project(extra = Vector(path.value))
    assertEquals(study.resolveMetadata(raw), Left(BidsError.MissingMetadata(path)))

  test("different entities, suffixes, and sibling directories do not contribute"):
    val study = project(documents = Vector(
      "task-other_bold.json" -> obj("WrongTask" -> JsonValue.Bool(true)),
      "task-rest_events.json" -> obj("WrongSuffix" -> JsonValue.Bool(true)),
      "sub-02/sub-02_task-rest_bold.json" -> obj("WrongSubject" -> JsonValue.Bool(true)),
      "sub-01/anat/task-rest_bold.json" -> obj("Sibling" -> JsonValue.Bool(true)),
      "task-rest_bold.json" -> obj("TaskName" -> JsonValue.Str("Rest"))
    ))
    assertEquals(value(study.metadata(raw)), obj("TaskName" -> JsonValue.Str("Rest")))

  test("direct metadata access remains explicit and excludes inherited values"):
    val study = project(documents = Vector(
      "task-rest_bold.json" -> obj("TaskName" -> JsonValue.Str("Rest")),
      "sub-01/func/sub-01_task-rest_run-01_bold.json" -> obj("RepetitionTime" -> JsonValue.Num(2.0))
    ))
    assertEquals(value(study.metadata(raw, inherit = false)), obj("RepetitionTime" -> JsonValue.Num(2.0)))

  test("unsafe target paths return typed failures"):
    val resolver = BidsMetadataResolver(BidsManifest(Vector.empty), Map.empty)
    assert(resolver.resolve(BidsPath("../outside_bold.nii.gz")).isLeft)
    assert(resolver.resolve(BidsPath("/outside_bold.nii.gz")).isLeft)

  test("standalone resolver and copied projects use the supplied immutable inventory"):
    val study = project(documents = Vector("task-rest_bold.json" -> obj("RepetitionTime" -> JsonValue.Num(1.0))))
    val resolver = BidsMetadataResolver(study.manifest, study.sidecars, study.derivatives)
    assertEquals(resolver.resolve(raw), study.resolveMetadata(raw))
    val edited = study.copy(sidecars = Map(BidsPath("task-rest_bold.json") -> obj("RepetitionTime" -> JsonValue.Num(2.0))))
    assertEquals(value(edited.metadata(raw)).fields("RepetitionTime"), JsonValue.Num(2.0))
    assertEquals(value(study.metadata(raw)).fields("RepetitionTime"), JsonValue.Num(1.0))
