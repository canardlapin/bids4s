package bids4s.benchmarks

import bids4s.*
import bids4s.io.*

import java.nio.file.{Path, Paths}
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.*

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
class BidsLayoutBenchmarks:
  private var root: Path = scala.compiletime.uninitialized
  private var project: BidsProject = scala.compiletime.uninitialized
  private var subjectQuery: BidsQuery = scala.compiletime.uninitialized
  private var subjectTaskQuery: BidsQuery = scala.compiletime.uninitialized
  private var runPresentQuery: BidsQuery = scala.compiletime.uninitialized
  private var runAbsentQuery: BidsQuery = scala.compiletime.uninitialized
  private var runOneQuery: BidsQuery = scala.compiletime.uninitialized
  private var metadataTarget: BidsPath = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    root =
      sys.props
        .get("bids4s.benchmark.root")
        .orElse(sys.env.get("BIDS4S_BENCHMARK_ROOT"))
        .map(Paths.get(_))
        .getOrElse(
          throw new IllegalArgumentException(
            "set -Dbids4s.benchmark.root=<fixture> or BIDS4S_BENCHMARK_ROOT"
          )
        )
    project = loadProject()
    subjectQuery = checked(BidsQuery.exact(EntityKey.Subject, "001"))
    subjectTaskQuery =
      checked(
        BidsQuery.exact(
          Vector(EntityKey.Subject -> "001", EntityKey.Task -> "rest")
        )
      )
    runPresentQuery = BidsQuery.present(EntityKey.Run)
    runAbsentQuery = BidsQuery.absent(EntityKey.Run)
    runOneQuery = checked(BidsQuery.exact(EntityKey.Run, "1"))
    metadataTarget =
      BidsPath(
        "sub-001/ses-01/func/sub-001_ses-01_task-rest_run-01_bold.nii.gz"
      )

  @Benchmark
  def index(): BidsProject =
    loadProject()

  @Benchmark
  def metadata(): JsonValue.Obj =
    checked(project.metadata(metadataTarget))

  @Benchmark
  def queryRawBold(): Vector[BidsFile] =
    project.funcScans()

  @Benchmark
  def querySubject(): Vector[BidsFile] =
    project.query(subjectQuery)

  @Benchmark
  def querySubjectTask(): Vector[BidsFile] =
    project.query(subjectTaskQuery)

  @Benchmark
  def queryRunPresent(): Vector[BidsFile] =
    project.query(runPresentQuery)

  @Benchmark
  def queryRunAbsent(): Vector[BidsFile] =
    project.query(runAbsentQuery)

  @Benchmark
  def queryRunOne(): Vector[BidsFile] =
    project.query(runOneQuery)

  @Benchmark
  def subjectIds(): Vector[String] =
    project.subjects()

  private def loadProject(): BidsProject =
    val loaded = checked(BidsProjectLoader.load(root))
    val _ = loaded.entityValues(EntityKey.Subject)
    loaded

  private def checked[A](result: Either[BidsError, A]): A =
    result.fold(error => throw new IllegalStateException(error.message), identity)
