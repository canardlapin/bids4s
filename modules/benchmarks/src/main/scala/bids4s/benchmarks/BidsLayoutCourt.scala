package bids4s.benchmarks

import bids4s.*
import bids4s.io.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}
import java.security.MessageDigest

object BidsLayoutCourt:
  def main(args: Array[String]): Unit =
    val root =
      args.headOption
        .map(Paths.get(_).toAbsolutePath.normalize())
        .getOrElse(throw new IllegalArgumentException("usage: BidsLayoutCourt <fixture-root>"))
    val project = checked(BidsProjectLoader.load(root))
    val subjectQuery = checked(BidsQuery.exact(EntityKey.Subject, "001"))
    val subjectTaskQuery =
      checked(
        BidsQuery.exact(
          Vector(EntityKey.Subject -> "001", EntityKey.Task -> "rest")
        )
      )
    val runPresentQuery = BidsQuery.present(EntityKey.Run)
    val runAbsentQuery = BidsQuery.absent(EntityKey.Run)
    val runOneQuery = checked(BidsQuery.exact(EntityKey.Run, "1"))
    val metadataTarget =
      BidsPath("sub-001/ses-01/func/sub-001_ses-01_task-rest_run-01_bold.nii.gz")

    val allFiles = project.paths().map(_.value)
    val rawBold = project.funcScans().map(_.path.value)
    val derivativeBold = project.preprocScans().map(_.path.value)
    val subjectFiles = project.paths(subjectQuery).map(_.value)
    val subjectTaskFiles = project.paths(subjectTaskQuery).map(_.value)
    val runPresentFiles = project.paths(runPresentQuery).map(_.value)
    val runAbsentFiles = project.paths(runAbsentQuery).map(_.value)
    val runOneFiles = project.paths(runOneQuery).map(_.value)
    val subjectIds = project.subjects()
    val metadata = checked(project.metadata(metadataTarget))

    println(
      s"""|{
          |  "implementation": "bids4s",
          |  "validation": {
          |    "all_files": ${pathResult(allFiles)},
          |    "derivative_bold": ${pathResult(derivativeBold)},
          |    "raw_bold": ${pathResult(rawBold)},
          |    "run_absent_files": ${pathResult(runAbsentFiles)},
          |    "run_one_files": ${pathResult(runOneFiles)},
          |    "run_present_files": ${pathResult(runPresentFiles)},
          |    "subject_files": ${pathResult(subjectFiles)},
          |    "subject_ids": ${pathResult(subjectIds)},
          |    "subject_task_files": ${pathResult(subjectTaskFiles)},
          |    "target_metadata": {
          |      "RepetitionTime": ${number(metadata, "RepetitionTime")},
          |      "TaskName": ${string(metadata, "TaskName")}
          |    }
          |  }
          |}""".stripMargin
    )

  private def pathResult(values: Vector[String]): String =
    val sorted = values.sorted
    s"""{"count": ${sorted.length}, "checksum": "${checksum(sorted)}"}"""

  private def checksum(values: Vector[String]): String =
    val digest =
      MessageDigest
        .getInstance("SHA-256")
        .digest(values.mkString("\n").getBytes(StandardCharsets.UTF_8))
    digest.map(byte => f"${byte & 0xff}%02x").mkString

  private def number(metadata: JsonValue.Obj, key: String): String =
    metadata.fields.get(key).flatMap(_.asNumber).map(_.toString).getOrElse("null")

  private def string(metadata: JsonValue.Obj, key: String): String =
    metadata.fields
      .get(key)
      .flatMap(_.asString)
      .map(value => "\"" + jsonEscape(value) + "\"")
      .getOrElse("null")

  private def jsonEscape(value: String): String =
    value.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case char => char.toString
    }

  private def checked[A](result: Either[BidsError, A]): A =
    result.fold(error => throw new IllegalStateException(error.message), identity)
