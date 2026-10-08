package example

import bids4s.*

object FirstContact:
  val name =
    BidsName.parse("sub-01_task-rest_run-1_bold.nii.gz")

  val query =
    BidsQuery.exact(EntityKey.Subject, "01", scope = BidsScope.Raw)

  val filesWithRuns =
    BidsQuery.present(EntityKey.Run)

  val firstRun =
    BidsQuery.run(1)

  def explainMetadata(project: BidsProject, path: BidsPath): Either[BidsError, Vector[BidsMetadataValue]] =
    project.resolveMetadata(path).map(_.history("RepetitionTime"))

  def fieldmaps(project: BidsProject): BidsFieldmapReport =
    BidsFieldmapResolver.resolve(project.manifest, BidsMetadataResolver(project.manifest, project.sidecars, project.derivatives))
