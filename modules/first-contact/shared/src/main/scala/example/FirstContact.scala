package example

import bids4s.*

object FirstContact:
  val name: BidsName =
    BidsName
      .parse("sub-01_task-rest_run-1_bold.nii.gz")
      .fold(error => throw new IllegalArgumentException(error.message), identity)

  val query: BidsQuery =
    (for
      subject <- EntityFilter.from(EntityKey.Subject, "01")
      result <- BidsQuery.from(
        filters = Vector(subject),
        matchMode = MatchMode.Exact,
        scope = BidsScope.Raw
      )
    yield result).fold(
      error => throw new IllegalArgumentException(error.message),
      identity
    )
