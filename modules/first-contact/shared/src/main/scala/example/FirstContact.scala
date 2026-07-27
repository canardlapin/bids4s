package example

import bids4s.*

object FirstContact:
  val name =
    BidsName.parse("sub-01_task-rest_run-1_bold.nii.gz")

  val query =
    BidsQuery.exact(EntityKey.Subject, "01", scope = BidsScope.Raw)
