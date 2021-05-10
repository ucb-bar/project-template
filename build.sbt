import Tests._

// This gives us a nicer handle to the root project instead of using the
// implicit one
lazy val chipyardRoot = Project("chipyardRoot", file("."))

def filenames(tempDir: File, fs: Seq[File]): Seq[String] =
for(f <- fs) yield {
  sbtassembly.AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
    case (path, base, subDirPath, false) => subDirPath
    case (jar, base, subJarPath, true) => jar + ":" + subJarPath
  }
}

// custom sbtassemblly.MergeStrategy
// removes merge candidates that have 'rocketchip' in the name of their JAR
// hopefully leaving only a single candidate to be chosen as the member of 
// our assembly JAR.  If there's still multiple candidates, error.
val notRocketMergeStrategy = new sbtassembly.MergeStrategy {
  val name = "notRocket"
  def apply(tempDir: File, path: String, files: Seq[File]) = {
    val filtered = files collect { f =>
      sbtassembly.AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
        case (jar, _, _, true) if !jar.toString.contains("rocketchip") => f
      }
    }
    if (filtered.size == 1) Right(Seq(filtered.head -> path))
    else Left("still have multiple files after removing rocketchip for same target path:" +
      filenames(tempDir, filtered).mkString("\n", "\n", "")
      )
  }
}

// custom sbtassemblly.MergeStrategy
// keeps merge candidates that have 'rocketchip' in the name of their JAR
// hopefully leaving only a single candidate to be chosen as the member of 
// our assembly JAR.  If there's still multiple candidates, error.
val useRocketMergeStrategy = new sbtassembly.MergeStrategy {
  val name = "useRocket"
  def apply(tempDir: File, path: String, files: Seq[File]) = {
    val filtered = files collect { f =>
      sbtassembly.AssemblyUtils.sourceOfFileForMerge(tempDir, f) match {
        case (jar, _, _, true) if jar.toString.contains("rocketchip") => f
      }
    }
    if (filtered.size == 1) Right(Seq(filtered.head -> path))
    else Left("multiple candidates have rocketchip in their jar name for target:" +
      filenames(tempDir, filtered).mkString("\n", "\n", "")
      )
  }
}

lazy val commonSettings = Seq(
  organization := "edu.berkeley.cs",
  version := "1.3",
  scalaVersion := "2.12.10",
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
    // Discard Metadata, it's irrelevant
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    // When any of our dependencies are different versions than those of firrtl.jar, there will be conflicts
    // When this occurs, pick last one which is stuff in .ivy2 (ie. not firrtl.jar)
    case PathList(xs @ _*) if xs.last.endsWith(".class") || xs.last.endsWith(".properties") => MergeStrategy.last
    // Just take the last matching joda/time/tz/data resource files
    case PathList("org", "joda", "time", "tz", "data", xs @ _*) => MergeStrategy.last
    // Use the file that doesn't come from rocketchip because we've overridden it
    case PathList(xs @ _*) if xs.last.equals("emulator.cc") => notRocketMergeStrategy
    case PathList("vsrc", "SimDTM.v") => useRocketMergeStrategy
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  scalacOptions ++= Seq("-deprecation","-unchecked","-Xsource:2.11"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
  unmanagedBase := (chipyardRoot / unmanagedBase).value,
  allDependencies := {
    // drop specific maven dependencies in subprojects in favor of Chipyard's version
    val dropDeps = Seq(
      ("edu.berkeley.cs", "firrtl"),
      ("edu.berkeley.cs", "chisel3"),
      ("edu.berkeley.cs", "rocketchip"),
      ("edu.berkeley.cs", "chisel-iotesters"),
      ("edu.berkeley.cs", "treadle"),
      ("edu.berkeley.cs", "firrtl-interpreter"))

    allDependencies.value.filterNot { dep =>
      dropDeps.contains((dep.organization, dep.name))
    }
  },
  exportJars := true,
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases"),
    Resolver.mavenLocal))

val rocketChipDir = file("generators/rocket-chip")

lazy val firesimAsLibrary = sys.env.get("FIRESIM_STANDALONE") == None
lazy val firesimDir = if (firesimAsLibrary) {
  file("sims/firesim/sim/")
} else {
  file("../../sim")
}

/**
  * It has been a struggle for us to override settings in subprojects.
  * An example would be adding a dependency to rocketchip on midas's targetutils library,
  * or replacing dsptools's maven dependency on chisel with the local chisel project.
  *
  * This function works around this by specifying the project's root at src/ and overriding
  * scalaSource and resourceDirectory.
  */
def freshProject(name: String, dir: File): Project = {
  Project(id = name, base = dir / "src")
    .settings(
      scalaSource in Compile := baseDirectory.value / "main" / "scala",
      resourceDirectory in Compile := baseDirectory.value / "main" / "resources"
    )
}

// Fork each scala test for now, to work around persistent mutable state
// in Rocket-Chip based generators
def isolateAllTests(tests: Seq[TestDefinition]) = tests map { test =>
  val options = ForkOptions()
  new Group(test.name, Seq(test), SubProcess(options))
} toSeq

// Subproject definitions begin

// -- Rocket Chip --

// This needs to stay in sync with the chisel3 and firrtl git submodules
val chiselVersion = "3.4.1"
lazy val chiselRef = ProjectRef(workspaceDirectory / "chisel3", "chisel")
lazy val chiselLib = "edu.berkeley.cs" %% "chisel3" % chiselVersion
lazy val chiselLibDeps = (chiselRef / Keys.libraryDependencies)
// While not built from source, *must* be in sync with the chisel3 git submodule
// Building from source requires extending sbt-sriracha or a similar plugin and
//   keeping scalaVersion in sync with chisel3 to the minor version
lazy val chiselPluginLib = "edu.berkeley.cs" % "chisel3-plugin" % chiselVersion cross CrossVersion.full

val firrtlVersion = "1.4.1"
lazy val firrtlRef = ProjectRef(workspaceDirectory / "firrtl", "firrtl")
lazy val firrtlLib = "edu.berkeley.cs" %% "firrtl" % firrtlVersion
val firrtlLibDeps = settingKey[Seq[sbt.librarymanagement.ModuleID]]("FIRRTL Library Dependencies sans antlr4")
Global / firrtlLibDeps := {
  // drop antlr4 compile dep. but keep antlr4-runtime dep. (compile needs the plugin to be setup)
  (firrtlRef / Keys.libraryDependencies).value.filterNot(_.name == "antlr4")
}

 // Rocket-chip dependencies (subsumes making RC a RootProject)
lazy val hardfloat  = (project in rocketChipDir / "hardfloat")
  .sourceDependency(chiselRef, chiselLib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(libraryDependencies ++= chiselLibDeps.value)
  .dependsOn(midasTargetUtils)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketMacros  = (project in rocketChipDir / "macros")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketConfig = (project in rocketChipDir / "api-config-chipsalliance/build-rules/sbt")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )

lazy val rocketchip = freshProject("rocketchip", rocketChipDir)
  .sourceDependency(chiselRef, chiselLib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(libraryDependencies ++= chiselLibDeps.value)
  .dependsOn(hardfloat, rocketMacros, rocketConfig)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value,
      "org.json4s" %% "json4s-jackson" % "3.6.1",
      "org.scalatest" %% "scalatest" % "3.2.0" % "test"
    )
  )
  .settings( // Settings for scalafix
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions += "-Ywarn-unused-import"
  )
lazy val rocketLibDeps = (rocketchip / Keys.libraryDependencies)

// -- Chipyard-managed External Projects --

lazy val firrtl_interpreter = (project in file("tools/firrtl-interpreter"))
  .sourceDependency(firrtlRef, firrtlLib)
  .settings(commonSettings)
  .settings(libraryDependencies ++= (Global / firrtlLibDeps).value)
lazy val firrtlInterpreterLibDeps = (firrtl_interpreter / Keys.libraryDependencies)

lazy val treadle = (project in file("tools/treadle"))
  .sourceDependency(firrtlRef, firrtlLib)
  .settings(commonSettings)
  .settings(libraryDependencies ++= (Global / firrtlLibDeps).value)
lazy val treadleLibDeps = (treadle / Keys.libraryDependencies)

lazy val chisel_testers = (project in file("tools/chisel-testers"))
  .sourceDependency(chiselRef, chiselLib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(libraryDependencies ++= chiselLibDeps.value)
  .dependsOn(firrtl_interpreter, treadle)
  .settings(libraryDependencies ++= firrtlInterpreterLibDeps.value)
  .settings(libraryDependencies ++= treadleLibDeps.value)
  .settings(commonSettings)
lazy val chiselTestersLibDeps = (chisel_testers / Keys.libraryDependencies)

// -- Normal Projects --

// Contains annotations & firrtl passes you may wish to use in rocket-chip without
// introducing a circular dependency between RC and MIDAS
lazy val midasTargetUtils = ProjectRef(firesimDir, "targetutils")

lazy val testchipip = (project in file("generators/testchipip"))
  .dependsOn(rocketchip, sifive_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)
lazy val testchipipLib = "edu.berkeley.cs" %% "testchipip" % "1.0-020719-SNAPSHOT"

lazy val chipyard = (project in file("generators/chipyard"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(rocketchip, boom, hwacha, sifive_blocks, sifive_cache, utilities, iocell,
    sha3, // On separate line to allow for cleaner tutorial-setup patches
    dsptools, `rocket-dsptools`,
    gemmini, icenet, tracegen, cva6, nvdla, sodor)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val tracegen = (project in file("generators/tracegen"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(rocketchip, sifive_cache, boom, utilities)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val utilities = (project in file("generators/utilities"))
  .sourceDependency(testchipip, testchipipLib)
  .settings(commonSettings)

lazy val icenet = (project in file("generators/icenet"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val hwacha = (project in file("generators/hwacha"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val boom = (project in file("generators/boom"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val cva6 = (project in file("generators/cva6"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sodor = (project in file("generators/riscv-sodor"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sha3 = (project in file("generators/sha3"))
  .dependsOn(rocketchip, chisel_testers, midasTargetUtils)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(libraryDependencies ++= chiselTestersLibDeps.value)
  .settings(commonSettings)

lazy val gemmini = (project in file("generators/gemmini"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(rocketchip, chisel_testers)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(libraryDependencies ++= chiselTestersLibDeps.value)
  .settings(commonSettings)

lazy val nvdla = (project in file("generators/nvdla"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val iocell = (project in file("./tools/barstools/iocell/"))
  .sourceDependency(chiselRef, chiselLib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(libraryDependencies ++= chiselLibDeps.value)
  .settings(commonSettings)

lazy val tapeout = (project in file("./tools/barstools/tapeout/"))
  .dependsOn(chisel_testers, chipyard) // must depend on chipyard to get scala resources
  .settings(libraryDependencies ++= chiselTestersLibDeps.value)
  .settings(commonSettings)

lazy val mdf = (project in file("./tools/barstools/mdf/scalalib/"))
  .settings(commonSettings)

lazy val barstoolsMacros = (project in file("./tools/barstools/macros/"))
  .sourceDependency(chiselRef, chiselLib)
  .settings(addCompilerPlugin(chiselPluginLib))
  .settings(libraryDependencies ++= chiselLibDeps.value)
  .dependsOn(firrtl_interpreter, mdf, chisel_testers)
  .settings(libraryDependencies ++= chiselTestersLibDeps.value)
  .settings(libraryDependencies ++= firrtlInterpreterLibDeps.value)
  .enablePlugins(sbtassembly.AssemblyPlugin)
  .settings(commonSettings)

lazy val dsptools = freshProject("dsptools", file("./tools/dsptools"))
  .dependsOn(chisel_testers)
  .settings(libraryDependencies ++= chiselTestersLibDeps.value)
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "spire" % "0.16.2",
      "org.scalanlp" %% "breeze" % "1.1",
      "junit" % "junit" % "4.13" % "test",
      "org.scalatest" %% "scalatest" % "3.0.+" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
  ))

lazy val `rocket-dsptools` = freshProject("rocket-dsptools", file("./tools/dsptools/rocket"))
  .dependsOn(rocketchip, dsptools)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sifive_blocks = (project in file("generators/sifive-blocks"))
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val sifive_cache = (project in file("generators/sifive-cache"))
  .settings(
    commonSettings,
    scalaSource in Compile := baseDirectory.value / "design/craft")
  .dependsOn(rocketchip)
  .settings(libraryDependencies ++= rocketLibDeps.value)

// Library components of FireSim
lazy val midas      = ProjectRef(firesimDir, "midas")
lazy val firesimLib = ProjectRef(firesimDir, "firesimLib")

lazy val firechip = (project in file("generators/firechip"))
  .sourceDependency(testchipip, testchipipLib)
  .dependsOn(chipyard, midasTargetUtils, midas, firesimLib % "test->test;compile->compile")
  .settings(
    commonSettings,
    testGrouping in Test := isolateAllTests( (definedTests in Test).value ),
    testOptions in Test += Tests.Argument("-oF")
  )
lazy val fpga_shells = (project in file("./fpga/fpga-shells"))
  .dependsOn(rocketchip, sifive_blocks)
  .settings(libraryDependencies ++= rocketLibDeps.value)
  .settings(commonSettings)

lazy val fpga_platforms = (project in file("./fpga"))
  .dependsOn(chipyard, fpga_shells)
  .settings(commonSettings)
