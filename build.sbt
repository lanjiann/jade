organization := "org.ucombinator"
name := "jade"
version := "0.1-SNAPSHOT"

scalaVersion := "2.12.4"

// For the plugin: "com.artima.supersafe" % "sbtplugin" % "1.1.3"
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

libraryDependencies ++= Seq(
  "org.rogach" %% "scallop" % "3.1.1",
  "org.ow2.asm" % "asm" % "6.0",
  "org.ow2.asm" % "asm-commons" % "6.0",
  "org.ow2.asm" % "asm-tree" % "6.0",
  "org.ow2.asm" % "asm-analysis" % "6.0",
  "org.ow2.asm" % "asm-util" % "6.0",
  // "org.ow2.asm" % "asm-xml" % "6.0",
  "org.scalactic" %% "scalactic" % "3.0.5",
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "commons-io" % "commons-io" % "2.6",
  "org.apache.maven" % "maven-core" % "3.5.2", // 3.5.2 has self conflicts in its own dependencies
  "org.apache.maven" % "maven-compat" % "3.5.2",
  "org.jgrapht" % "jgrapht-core" % "1.1.0",
  "org.jgrapht" % "jgrapht-ext" % "1.1.0"
// "com.google.guava" % "guava" % "24.0-jre",
)

// Flags to 'scalac'.  Try to get as much error and warning detection as possible.
scalacOptions ++= Seq(
  // Emit warning and location for usages of deprecated APIs.
  "-deprecation",
  // Explain type errors in more detail.
  "-explaintypes",
  // Emit warning and location for usages of features that should be imported explicitly.
  "-feature",
  // Generates faster bytecode by applying optimisations to the program
  "-opt:l:inline",
  // Enable additional warnings where generated code depends on assumptions.
  "-unchecked",
  "-Xlint:_")

javacOptions in compile ++= Seq(
  // Turn on all warnings
  "-Xlint")

assemblyOutputPath in assembly := new File("lib/jade/jade.jar")

// Create merge strategies that do not cause warnings
def quiet(mergeStragegy: sbtassembly.MergeStrategy): sbtassembly.MergeStrategy = new sbtassembly.MergeStrategy {
  val name = "quiet:" + mergeStragegy.name
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
    mergeStragegy(tempDir, path, files)

  override def notifyThreshold = 1
  override def detailLogLevel = Level.Info
  override def summaryLogLevel = Level.Info
}

lazy val quietDiscard = quiet(MergeStrategy.discard)
lazy val quietRename = quiet(MergeStrategy.rename)

assemblyMergeStrategy in assembly := {
  case PathList(file) if List(
    "library.properties", // from scala-library
    "rootdoc.txt", // from scala-library
    "reflect.properties", // from scala-reflect
    "module-info.class", // from asm-6.0
    "about.html" // from org.eclipse.sisu.plexus-0.3.3 and org.eclipse.sisu.inject-0.3.3
  ).contains(file) => quietDiscard

  case PathList("META-INF", "maven", xs @ _*) => MergeStrategy.deduplicate
  case PathList("META-INF", xs @ _*) => quietRename

  case _ => MergeStrategy.deduplicate
}
