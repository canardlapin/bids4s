import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport.*
import sbtcrossproject.CrossPlugin.autoImport.*
import scalajscrossproject.ScalaJSCrossPlugin.autoImport.*
import sbtversionpolicy.Compatibility

ThisBuild / organization := "io.github.canardlapin"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible
ThisBuild / homepage := Some(url("https://github.com/canardlapin/bids4s"))
ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/canardlapin/bids4s"),
    "scm:git:git@github.com:canardlapin/bids4s.git"
  )
)
ThisBuild / licenses := List(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")
)
ThisBuild / developers := List(
  Developer(
    "canardlapin",
    "canardlapin",
    "307091466+canardlapin@users.noreply.github.com",
    url("https://github.com/canardlapin")
  )
)
ThisBuild / versionPolicyPreviousVersions := Seq.empty
Global / excludeLintKeys += versionPolicyPreviousVersions

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Werror",
    "-Wunused:all",
    "-Wvalue-discard",
    "-Wnonunit-statement",
    "-Xmax-inlines:64"
  ),
  Test / fork := false,
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit" % "1.3.0" % Test,
    "org.scalameta" %%% "munit-scalacheck" % "1.3.0" % Test
  )
)

lazy val jsSettings = Seq(
  scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  Test / jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()
)

lazy val core =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/core"))
    .settings(commonSettings)
    .settings(
      name := "bids4s",
      description := "Typed, immutable BIDS parsing, validation, querying, metadata, tables, and confound selection for Scala 3.",
      libraryDependencies += "org.typelevel" %%% "cats-core" % "2.12.0"
    )
    .jvmSettings(
      libraryDependencies += "org.typelevel" %% "cats-effect" % "3.5.4",
      coverageFailOnMinimum := true,
      coverageMinimumStmtTotal := 80.0,
      coverageMinimumBranchTotal := 70.0
    )
    .jsSettings(jsSettings)

lazy val coreJVM = core.jvm
lazy val coreJS = core.js

lazy val firstContact =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Full)
    .in(file("modules/first-contact"))
    .dependsOn(core)
    .disablePlugins(SbtVersionPolicyPlugin)
    .settings(commonSettings)
    .settings(
      name := "bids4s-first-contact",
      description := "Non-published downstream-package specimen for the public bids4s API.",
      publish / skip := true
    )
    .jsSettings(jsSettings)

lazy val firstContactJVM = firstContact.jvm
lazy val firstContactJS = firstContact.js

lazy val benchmarks =
  project
    .in(file("modules/benchmarks"))
    .dependsOn(coreJVM)
    .enablePlugins(JmhPlugin)
    .disablePlugins(SbtVersionPolicyPlugin)
    .settings(commonSettings)
    .settings(
      name := "bids4s-benchmarks",
      description := "Non-published JMH and PyBIDS comparison court for bids4s.",
      publish / skip := true
    )

lazy val docs =
  project
    .in(file("site"))
    .dependsOn(coreJVM)
    .enablePlugins(TypelevelSitePlugin)
    .disablePlugins(SbtVersionPolicyPlugin)
    .settings(
      name := "bids4s-docs",
      description := "Executable guides and reference documentation for bids4s.",
      publish / skip := true,
      mdocExtraArguments += "--no-link-hygiene"
    )

lazy val root =
  project
    .in(file("."))
    .aggregate(coreJVM, coreJS, firstContactJVM, firstContactJS, benchmarks, docs)
    .settings(
      name := "bids4s-root",
      publish / skip := true
    )

addCommandAlias("compileAll", ";coreJVM/compile;coreJS/compile;firstContactJVM/compile;firstContactJS/compile")
addCommandAlias("testAll", ";coreJVM/test;coreJS/test;firstContactJVM/test;firstContactJS/test")
addCommandAlias("benchmarkSmoke", ";benchmarks/compile;benchmarks/Jmh/compile")
addCommandAlias("coverageJVM", ";coverage;coreJVM/test;coreJVM/coverageReport")
