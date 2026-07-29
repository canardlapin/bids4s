package bids4s

class BidsQuerySuite extends munit.FunSuite:
  private def value[A](e: Either[BidsError, A]): A =
    e.fold(err => fail(err.message), identity)

  private def filter(key: EntityKey, value0: String): EntityFilter =
    value(EntityFilter.from(key, value0))

  private def query(
      filename: Vector[String] = Vector(".*"),
      filters: Vector[EntityFilter] = Vector.empty,
      matchMode: MatchMode = MatchMode.Regex,
      requireEntity: Boolean = false,
      scope: BidsScope = BidsScope.All,
      pipeline: Option[PipelineName] = None,
      strict: Boolean = true
  ): BidsQuery =
    value(BidsQuery.from(filename, filters, matchMode, requireEntity, scope, pipeline, strict))

  private val manifest =
    BidsManifest.fromRelativePaths(
      Vector(
        "participants.tsv",
        "sub-01/anat/sub-01_T1w.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_events.tsv",
        "derivatives/fmriprep/sub-01/func/sub-01_task-taskA_space-MNI_desc-preproc_bold.nii.gz"
      ),
      derivatives = Vector(DerivativeRoot(BidsPath("derivatives/fmriprep"), PipelineName("fmriprep")))
    )

  test("query supports exact entity matching"):
    val hits = manifest.paths(
      query(
        filename = Vector("bold\\.nii\\.gz$"),
        matchMode = MatchMode.Exact,
        scope = BidsScope.Raw,
        filters = Vector(filter(EntityKey.Subject, "01"), filter(EntityKey.Task, "taskA"))
      )
    )

    assertEquals(hits.map(_.value), Vector("sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz"))

  test("exact constructs the common single-entity query without weakening validation"):
    val concise = value(BidsQuery.exact(EntityKey.Subject, "01", scope = BidsScope.Raw))
    val expanded =
      query(
        filters = Vector(filter(EntityKey.Subject, "01")),
        matchMode = MatchMode.Exact,
        scope = BidsScope.Raw
      )

    assertEquals(concise, expanded)
    assertEquals(
      manifest.paths(concise).map(_.value),
      Vector(
        "sub-01/anat/sub-01_T1w.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_events.tsv"
      )
    )
    assert(BidsQuery.exact(EntityKey.Subject, " ").isLeft)

  test("exact accepts several checked entity filters"):
    val concise =
      value(
        BidsQuery.exact(
          Vector(EntityKey.Subject -> "01", EntityKey.Task -> "taskA"),
          scope = BidsScope.Raw
        )
      )
    val expanded =
      query(
        filters = Vector(
          filter(EntityKey.Subject, "01"),
          filter(EntityKey.Task, "taskA")
        ),
        matchMode = MatchMode.Exact,
        scope = BidsScope.Raw
      )

    assertEquals(concise, expanded)
    assertEquals(
      manifest.paths(concise).map(_.value),
      Vector(
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_events.tsv"
      )
    )
    assert(BidsQuery.exact(Vector.empty).isLeft)
    assert(BidsQuery.exact(Vector(EntityKey.Subject -> " ")).isLeft)

  test("exact run matching follows PyBIDS padded integer semantics"):
    val padded =
      BidsManifest.fromRelativePaths(
        Vector(
          "sub-01/func/sub-01_task-rest_run-01_bold.nii.gz",
          "sub-01/func/sub-01_task-rest_echo-01_bold.nii.gz"
        )
      )

    Vector("1", "01", "001").foreach { run =>
      assertEquals(
        padded.paths(value(BidsQuery.exact(EntityKey.Run, run))).map(_.value),
        Vector("sub-01/func/sub-01_task-rest_run-01_bold.nii.gz")
      )
    }
    assertEquals(
      padded.paths(value(BidsQuery.run(1))).map(_.value),
      Vector("sub-01/func/sub-01_task-rest_run-01_bold.nii.gz")
    )
    assert(BidsQuery.run(-1).isLeft)
    assertEquals(
      padded.paths(value(BidsQuery.exact(EntityKey.Subject, "1"))),
      Vector.empty
    )
    assertEquals(
      padded.paths(value(BidsQuery.exact(EntityKey.Echo, "1"))),
      Vector.empty
    )
    assertEquals(
      padded.paths(value(BidsQuery.exact(EntityKey.Echo, "01"))).map(_.value),
      Vector("sub-01/func/sub-01_task-rest_echo-01_bold.nii.gz")
    )

  test("query supports regex entity matching"):
    val hits = manifest.paths(
      query(
        filename = Vector("bold\\.nii\\.gz$"),
        matchMode = MatchMode.Regex,
        scope = BidsScope.Raw,
        filters = Vector(filter(EntityKey.Subject, "0[1]"), filter(EntityKey.Task, "task.*"))
      )
    )

    assertEquals(hits.map(_.value), Vector("sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz"))

  test("requireEntity excludes files missing wildcard entity"):
    val lax = manifest.paths(
      query(
        filename = Vector("T1w\\.nii\\.gz$"),
        filters = Vector(filter(EntityKey.Task, ".*")),
        requireEntity = false,
        scope = BidsScope.Raw
      )
    )
    val strict = manifest.paths(
      query(
        filename = Vector("T1w\\.nii\\.gz$"),
        filters = Vector(filter(EntityKey.Task, ".*")),
        requireEntity = true,
        scope = BidsScope.Raw
      )
    )

    assertEquals(lax.map(_.value), Vector("sub-01/anat/sub-01_T1w.nii.gz"))
    assertEquals(strict, Vector.empty)

  test("presence queries express present, absent, and optional entities directly"):
    assertEquals(
      manifest.paths(BidsQuery.present(EntityKey.Run)).map(_.value),
      Vector(
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_events.tsv"
      )
    )
    assertEquals(
      manifest.paths(BidsQuery.absent(EntityKey.Run)).map(_.value),
      Vector(
        "derivatives/fmriprep/sub-01/func/sub-01_task-taskA_space-MNI_desc-preproc_bold.nii.gz",
        "participants.tsv",
        "sub-01/anat/sub-01_T1w.nii.gz"
      )
    )

    val optionalAcquisition =
      query(
        filters = Vector(EntityFilter.optional(EntityKey.Acquisition)),
        matchMode = MatchMode.Exact
      )
    assertEquals(manifest.paths(optionalAcquisition), manifest.paths())

  test("presence filters compose with value filters without sentinel strings"):
    val present = EntityFilter.present(EntityKey.Run)
    val absent = EntityFilter.absent(EntityKey.Acquisition)
    val hits =
      manifest.paths(
        query(
          filters = Vector(
            filter(EntityKey.Subject, "01"),
            present,
            absent
          ),
          matchMode = MatchMode.Exact
        )
      )

    assertEquals(present.presence, Some(EntityPresence.Present))
    assertEquals(present.values, Vector.empty)
    assertEquals(absent.presence, Some(EntityPresence.Absent))
    assertEquals(
      hits.map(_.value),
      Vector(
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_events.tsv"
      )
    )

  test("query separates raw and derivative scopes and pipelines"):
    val hits = manifest.paths(
      query(
        filename = Vector("bold\\.nii\\.gz$"),
        scope = BidsScope.Derivatives,
        pipeline = Some(PipelineName("fmriprep"))
      )
    )

    assertEquals(
      hits.map(_.value),
      Vector("derivatives/fmriprep/sub-01/func/sub-01_task-taskA_space-MNI_desc-preproc_bold.nii.gz")
    )

  test("glob matching applies to entity filters"):
    val hits = manifest.paths(
      query(
        filename = Vector(".*\\.nii\\.gz$"),
        matchMode = MatchMode.Glob,
        filters = Vector(filter(EntityKey.Task, "task?")),
        strict = true
      )
    )

    assertEquals(
      hits.map(_.value),
      Vector(
        "derivatives/fmriprep/sub-01/func/sub-01_task-taskA_space-MNI_desc-preproc_bold.nii.gz",
        "sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz"
      )
    )

  test("typed filename patterns validate regexes and escape exact names"):
    assert(QueryPattern.regex("[").isLeft)
    assert(BidsQuery.from(filename = Vector("[")).isLeft)

    val exact = value(QueryPattern.exact("sub-01_task-taskA_run-01_bold.nii.gz"))
    val query = value(BidsQuery.fromPatterns(Vector(exact), scope = BidsScope.Raw))
    val hits = manifest.paths(query)

    assertEquals(hits.map(_.value), Vector("sub-01/func/sub-01_task-taskA_run-01_bold.nii.gz"))

  test("query and filter smart constructors reject invalid states"):
    assert(EntityFilter.from(EntityKey.Subject, Vector.empty).isLeft)
    assert(EntityFilter.from(EntityKey.Subject, Vector(" ")).isLeft)
    assert(BidsQuery.from(filename = Vector.empty).isLeft)
    assert(BidsQuery.from(filename = Vector("["), filters = Vector(filter(EntityKey.Task, "["))).isLeft)

  test("checked query construction accumulates independent pattern and filter issues"):
    val report =
      BidsQuery
        .fromChecked(
          filename = Vector("[", "("),
          filters = Vector(filter(EntityKey.Task, "["))
        )
        .left
        .toOption
        .getOrElse(fail("expected validation issues"))

    assertEquals(report.errors.length, 3)
    assertEquals(
      report.issues.flatMap(_.field),
      Vector("filename[0]", "filename[1]", "filters[0].values[0]")
    )
    assert(BidsQuery.from(filename = Vector("[", "(")).isLeft)
