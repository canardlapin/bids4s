package bids4s

class PreprocessedMetadataSuite extends munit.FunSuite:
  private val root = DerivativeRoot(BidsPath("derivatives/custom"), PipelineName("custom"))

  test("normalized anatomy resolution and confound sidecars preserve derivative roles"):
    val paths = Vector(
      "derivatives/custom/sub-01/anat/sub-01_space-MNI152NLin2009cAsym_res-2_desc-preproc_T1w.nii.gz",
      "derivatives/custom/sub-01/anat/sub-01_space-MNI152NLin2009cAsym_res-2_desc-brain_mask.nii.gz",
      "derivatives/custom/sub-01/func/sub-01_task-bart_run-1_desc-confounds_timeseries.json",
      "derivatives/custom/sub-01/func/sub-01_task-bart_run-1_desc-confounds_timeseries.tsv",
      "derivatives/custom/sub-01/func/sub-01_task-bart_run-1_confounds.json")
    val report = BidsManifest.fromRelativePathsChecked(paths, Vector(root))
    assertEquals(report.issues, Vector.empty)
    assertEquals(report.value.files.size, 5)
    assert(report.value.files.forall(_.scope == BidsScope.Derivatives))
    assertEquals(report.value.files.head.entities.get(EntityKey.Resolution), Some("2"))

  test("derivative support does not admit raw resolution or unknown sidecar extensions"):
    val raw = BidsManifest.fromRelativePathsChecked(Vector("sub-01/anat/sub-01_res-2_T1w.nii.gz"))
    assert(raw.issues.nonEmpty)
    val unknown = BidsManifest.fromRelativePathsChecked(Vector(
      "derivatives/custom/sub-01/func/sub-01_task-bart_desc-confounds_timeseries.exe"), Vector(root))
    assert(unknown.issues.nonEmpty)
