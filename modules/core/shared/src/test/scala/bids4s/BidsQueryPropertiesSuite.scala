package bids4s

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class BidsQueryPropertiesSuite extends ScalaCheckSuite:
  override def scalaCheckTestParameters =
    super.scalaCheckTestParameters.withMinSuccessfulTests(200)

  private val entityKeyGen: Gen[EntityKey] =
    Gen.oneOf(
      EntityKey.Subject,
      EntityKey.Session,
      EntityKey.Task,
      EntityKey.Run,
      EntityKey.Acquisition,
      EntityKey.Space
    )

  private val subjectGen: Gen[String] =
    Gen.choose(1, 12).map(value => f"$value%02d")

  private val sessionGen: Gen[Option[String]] =
    Gen.option(Gen.choose(1, 3).map(value => f"$value%02d"))

  private val taskGen: Gen[String] =
    Gen.oneOf("rest", "nback", "stroop")

  private val runGen: Gen[Option[String]] =
    Gen.option(Gen.choose(1, 4).map(value => f"$value%02d"))

  private val fileGen: Gen[String] =
    Gen.frequency(
      2 -> (for
        subject <- subjectGen
        session <- sessionGen
      yield
        val prefix = namePrefix(subject, session)
        s"${directory(subject, session)}/anat/${prefix}_T1w.nii.gz"),
      5 -> (for
        subject <- subjectGen
        session <- sessionGen
        task <- taskGen
        run <- runGen
        extension <- Gen.oneOf("bold.nii.gz", "events.tsv")
      yield
        val prefix = namePrefix(subject, session)
        val runPart = run.fold("")(value => s"_run-$value")
        s"${directory(subject, session)}/func/${prefix}_task-$task${runPart}_$extension"),
      1 -> Gen.oneOf("participants.tsv", "dataset_description.json", "README")
    )

  private val pathsGen: Gen[Vector[String]] =
    Gen.listOfN(48, fileGen).map(_.toVector)

  private val valueFilterGen: Gen[EntityFilter] =
    for
      key <- entityKeyGen
      first <- filterValue(key)
      second <- Gen.option(filterValue(key))
    yield
      EntityFilter
        .from(key, (first +: second.toVector).distinct)
        .fold(error => fail(error.message), identity)

  private val presenceFilterGen: Gen[EntityFilter] =
    for
      key <- entityKeyGen
      presence <- Gen.oneOf(EntityPresence.Present, EntityPresence.Absent, EntityPresence.Optional)
    yield
      presence match
        case EntityPresence.Present  => EntityFilter.present(key)
        case EntityPresence.Absent   => EntityFilter.absent(key)
        case EntityPresence.Optional => EntityFilter.optional(key)

  private val filterGen: Gen[EntityFilter] =
    Gen.frequency(3 -> valueFilterGen, 2 -> presenceFilterGen)

  private val queryGen: Gen[BidsQuery] =
    for
      filterCount <- Gen.choose(1, 3)
      filters <- Gen.listOfN(filterCount, filterGen)
      requireEntity <- Gen.oneOf(true, false)
      strict <- Gen.oneOf(true, false)
    yield
      BidsQuery
        .from(
          filters = filters.toVector,
          matchMode = MatchMode.Exact,
          requireEntity = requireEntity,
          strict = strict
        )
        .fold(error => fail(error.message), identity)

  property("indexed exact and presence queries equal the canonical scan"):
    forAll(pathsGen, queryGen) { (paths, query) =>
      val manifest = BidsManifest.fromRelativePaths(paths)
      val canonical = manifest.files.filter(_.matches(query)).sortBy(_.path.value)

      assertEquals(manifest.query(query), canonical)
    }

  property("query results do not depend on discovery order"):
    forAll(pathsGen, queryGen) { (paths, query) =>
      val forward = BidsManifest.fromRelativePaths(paths).paths(query)
      val reverse = BidsManifest.fromRelativePaths(paths.reverse).paths(query)

      assertEquals(forward, reverse)
    }

  property("padded run indices have one exact-query meaning"):
    forAll(subjectGen, taskGen, Gen.choose(1, 999), Gen.choose(1, 4)) {
      (subject, task, run, width) =>
        val digits = run.toString
        val runValue = "0" * math.max(0, width - digits.length) + digits
        val path = s"sub-$subject/func/sub-${subject}_task-${task}_run-${runValue}_bold.nii.gz"
        val manifest = BidsManifest.fromRelativePaths(Vector(path))
        val queryValues = Vector(run.toString, runValue, "0" * 3 + run.toString)

        queryValues.foreach { queryValue =>
          val query =
            BidsQuery
              .exact(EntityKey.Run, queryValue)
              .fold(error => fail(error.message), identity)
          assertEquals(manifest.paths(query).map(_.value), Vector(path))
        }
    }

  private def namePrefix(subject: String, session: Option[String]): String =
    s"sub-$subject" + session.fold("")(value => s"_ses-$value")

  private def directory(subject: String, session: Option[String]): String =
    s"sub-$subject" + session.fold("")(value => s"/ses-$value")

  private def filterValue(key: EntityKey): Gen[String] =
    key match
      case EntityKey.Subject     => subjectGen
      case EntityKey.Session     => Gen.choose(1, 4).map(value => f"$value%02d")
      case EntityKey.Task        => Gen.oneOf("rest", "nback", "stroop", "missing")
      case EntityKey.Run         => Gen.choose(1, 5).map(value => f"$value%02d")
      case EntityKey.Acquisition => Gen.oneOf("highres", "lowres")
      case EntityKey.Space       => Gen.oneOf("MNI", "fsaverage")
      case _                     => Gen.const("value")
