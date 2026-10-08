package bids4s

class BidsFieldmapSuite extends munit.FunSuite:
  private val bold = "sub-01/func/sub-01_task-rest_bold.nii.gz"
  private val ap = "sub-01/fmap/sub-01_dir-AP_epi.nii.gz"
  private val pa = "sub-01/fmap/sub-01_dir-PA_epi.nii.gz"
  private def str(v: String): JsonValue = JsonValue.Str(v)
  private def obj(fields: (String, JsonValue)*): JsonValue.Obj = JsonValue.Obj(fields.toMap)
  private def sidecar(image: String): String = image.stripSuffix(".nii.gz") + ".json"
  private def report(data: Vector[(String, JsonValue.Obj)], extra: Vector[(String, JsonValue.Obj)] = Vector.empty,
      entries: Vector[String] = Vector.empty): BidsFieldmapReport =
    val documents = data.map((p, j) => sidecar(p) -> j) ++ extra
    val manifest = BidsManifest.fromRelativePaths(data.map(_._1) ++ documents.map(_._1) ++ entries)
    val resolver = BidsMetadataResolver(manifest, documents.map((p, j) => BidsPath(p) -> j).toMap)
    BidsFieldmapResolver.resolve(manifest, resolver)

  test("all images sharing an identifier form one group with field-origin evidence"):
    val r = report(Vector(
      bold -> obj("B0FieldSource" -> str("pepolar")),
      ap -> obj("B0FieldIdentifier" -> str("pepolar")),
      pa -> obj("B0FieldIdentifier" -> str("pepolar"))
    ))
    assertEquals(r.issues, Vector.empty)
    assertEquals(r.associations.size, 1)
    val a = r.associations.head
    assertEquals(a.target, BidsPath(bold))
    assertEquals(a.sources.map(_.value), Vector(ap, pa))
    assertEquals(a.identifier, Some("pepolar"))
    assertEquals(a.evidence.map(_.field).sorted, Vector("B0FieldIdentifier", "B0FieldIdentifier", "B0FieldSource"))
    assert(a.evidence.exists(e => e.artifact == BidsPath(bold) && e.document == BidsPath(sidecar(bold))))

  test("identifier scope is a participant tree and can span sessions"):
    val sessionA = ap.replace("sub-01/fmap/", "sub-01/ses-A/fmap/").replace("sub-01_dir", "sub-01_ses-A_dir")
    val sessionB = pa.replace("sub-01/fmap/", "sub-01/ses-B/fmap/").replace("sub-01_dir", "sub-01_ses-B_dir")
    val other = ap.replace("sub-01", "sub-02")
    val r = report(Vector(bold -> obj("B0FieldSource" -> str("same")),
      sessionA -> obj("B0FieldIdentifier" -> str("same")), sessionB -> obj("B0FieldIdentifier" -> str("same")),
      other -> obj("B0FieldIdentifier" -> str("same"))))
    assertEquals(r.associations.head.sources.map(_.value), Vector(sessionA, sessionB))
    assert(!r.issues.exists(_.isError))

  test("source arrays retain separate estimation groups and valid self-reference"):
    val r = report(Vector(bold -> obj("B0FieldSource" -> JsonValue.Arr(Vector(str("a"), str("b")))),
      ap -> obj("B0FieldIdentifier" -> str("a"), "B0FieldSource" -> str("a")),
      pa -> obj("B0FieldIdentifier" -> str("b"))))
    assertEquals(r.associations.filter(_.target == BidsPath(bold)).map(_.identifier).sorted, Vector(Some("a"), Some("b")))
    assert(r.associations.exists(a => a.target == BidsPath(ap) && a.sources == Vector(BidsPath(ap))))
    assert(!r.issues.exists(_.isError))

  test("inheritance contributes the original defining sidecar"):
    val r = report(Vector(bold -> obj(), ap -> obj("B0FieldIdentifier" -> str("p"))),
      Vector("task-rest_bold.json" -> obj("B0FieldSource" -> str("p"))))
    assertEquals(r.associations.head.evidence.find(_.field == "B0FieldSource").map(_.document), Some(BidsPath("task-rest_bold.json")))

  test("missing identifiers never fall back to another participant or dataset"):
    val nested = "derivatives/prep/" + ap
    val r = report(Vector(bold -> obj("B0FieldSource" -> str("p")),
      ap.replace("sub-01", "sub-02") -> obj("B0FieldIdentifier" -> str("p")),
      nested -> obj("B0FieldIdentifier" -> str("p"))),
      entries = Vector("derivatives/prep/dataset_description.json"))
    assertEquals(r.associations, Vector.empty)
    assert(r.issues.exists(_.code == BidsFieldmapIssueCode.MissingIdentifier))

  test("current-dataset URIs and legacy participant-relative paths retain their explicit mechanism"):
    val modern = report(Vector(bold -> obj(), ap -> obj("IntendedFor" -> str("bids::" + bold))))
    val legacy = report(Vector(bold -> obj(), ap -> obj("IntendedFor" -> str(bold.stripPrefix("sub-01/")))))
    assertEquals(modern.associations, legacy.associations)
    assertEquals(modern.associations.head.mechanism, BidsFieldmapMechanism.IntendedFor)
    assert(legacy.issues.exists(_.code == BidsFieldmapIssueCode.LegacyIntendedFor))
    assert(!legacy.issues.exists(_.isError))

  test("a current-dataset URI is resolved relative to a nested dataset root"):
    val root = "derivatives/prep/"
    val r = report(Vector(root + bold -> obj(), root + ap -> obj("IntendedFor" -> str("bids::" + bold))),
      entries = Vector(root + "dataset_description.json"))
    assertEquals(r.associations.head.target, BidsPath(root + bold))
    assertEquals(r.associations.head.sources, Vector(BidsPath(root + ap)))

  test("missing, external and cross-boundary IntendedFor targets are explicit failures"):
    val nested = "derivatives/prep/" + bold
    val r = report(Vector(ap -> obj("IntendedFor" -> JsonValue.Arr(Vector(
      str("bids::sub-01/func/sub-01_task-missing_bold.nii.gz"), str("bids:remote:" + bold), str("bids::" + nested)))),
      nested -> obj()), entries = Vector("derivatives/prep/dataset_description.json"))
    assertEquals(r.associations, Vector.empty)
    assertEquals(r.issues.map(_.code).toSet, Set(BidsFieldmapIssueCode.MissingTarget,
      BidsFieldmapIssueCode.UnsupportedExternalReference, BidsFieldmapIssueCode.DatasetBoundary))

  test("malformed declaration types and empty arrays accumulate deterministic findings"):
    val r = report(Vector(bold -> obj("B0FieldSource" -> JsonValue.Arr(Vector.empty)),
      ap -> obj("B0FieldIdentifier" -> JsonValue.Num(1), "IntendedFor" -> JsonValue.Arr(Vector(str(bold), JsonValue.Null)))))
    assertEquals(r.issues.size, 3)
    assert(r.issues.forall(_.code == BidsFieldmapIssueCode.InvalidDeclaration))

  test("unsafe and noncanonical paths are never silently repaired"):
    Vector("../func/x.nii.gz", "/sub-01/x.nii.gz", "bids::sub-01/../x.nii.gz", "bids::sub-01//x.nii.gz",
      "bids::sub-01/./x.nii.gz", "bids::sub-01\\x.nii.gz").foreach { target =>
      val r = report(Vector(ap -> obj("IntendedFor" -> str(target))))
      assert(r.associations.isEmpty, target)
      assert(r.issues.exists(_.code == BidsFieldmapIssueCode.InvalidReference), target)
    }

  test("metadata ambiguity is reported rather than choosing one association declaration"):
    val r = report(Vector(ap -> obj("B0FieldIdentifier" -> str("a"))),
      Vector("sub-01/fmap/sub-01_epi.json" -> obj("B0FieldIdentifier" -> str("b"))))
    assert(r.issues.exists(_.code == BidsFieldmapIssueCode.MetadataResolution))
    assert(r.associations.isEmpty)

  test("input ordering and duplicated references do not change the resolved report"):
    val input = Vector(bold -> obj("B0FieldSource" -> JsonValue.Arr(Vector(str("p"), str("p")))),
      ap -> obj("B0FieldIdentifier" -> str("p")), pa -> obj("B0FieldIdentifier" -> str("p")))
    assertEquals(report(input), report(input.reverse))

  test("a valid dataset without declarations has an empty relationship report"):
    assertEquals(report(Vector(bold -> obj())), BidsFieldmapReport(Vector.empty, Vector.empty))

  test("both declaration mechanisms are retained without silently choosing precedence"):
    val r = report(Vector(bold -> obj("B0FieldSource" -> str("p")),
      ap -> obj("B0FieldIdentifier" -> str("p"), "IntendedFor" -> str("bids::" + bold))))
    assertEquals(r.associations.map(_.mechanism).toSet, BidsFieldmapMechanism.values.toSet)
    assert(!r.issues.exists(_.isError))

  test("a participant label in a filename cannot override its actual folder scope"):
    val misplaced = ap.replace("sub-01/fmap/", "sub-02/fmap/")
    val r = report(Vector(bold -> obj("B0FieldSource" -> str("p")), misplaced -> obj("B0FieldIdentifier" -> str("p"))))
    assert(r.issues.exists(_.code == BidsFieldmapIssueCode.InvalidParticipantScope))
    assert(r.issues.exists(_.code == BidsFieldmapIssueCode.MissingIdentifier))
    assert(r.associations.isEmpty)

  test("expanded many-to-many declarations are bounded and never silently reported complete"):
    val providers = (1 to 320).toVector.map(i =>
      s"sub-01/fmap/sub-01_run-${i}_epi.nii.gz" -> obj("B0FieldIdentifier" -> str("p")))
    val consumers = (1 to 320).toVector.map(i =>
      s"sub-01/func/sub-01_task-rest_run-${i}_bold.nii.gz" -> obj("B0FieldSource" -> str("p")))
    val r = report(providers ++ consumers)
    assert(r.associations.map(_.sources.size).sum <= 100000)
    assert(r.issues.exists(_.code == BidsFieldmapIssueCode.LimitExceeded))

  test("an existing non-NIfTI target is unsupported, not reported as missing"):
    val jsonPath = sidecar(bold)
    val r = report(Vector(bold -> obj(), ap -> obj("IntendedFor" -> str("bids::" + jsonPath))))
    assertEquals(r.issues.map(_.code), Vector(BidsFieldmapIssueCode.UnsupportedTarget))
    assert(r.associations.isEmpty)
