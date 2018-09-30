organization := "org.ucombinator"
name := "jade"
version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

// For the plugin: "com.artima.supersafe" % "sbtplugin" % "1.1.3"
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

libraryDependencies ++= {
  val scalatestVersion = "3.0.5"
  val asmVersion       = "6.2.1"
  val jgraphtVersion   = "1.2.0"
  val luceneVersion    = "5.5.5" // For the compatibility with org.apache.maven.indexer:indexer-core:6.0.0

  Seq(
    "org.rogach"             %% "scallop"                  % "3.1.3",
    "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
    "org.scalactic"          %% "scalactic"                % scalatestVersion,
    "org.scalatest"          %% "scalatest"                % scalatestVersion % Test,

    "org.ow2.asm" % "asm"          % asmVersion,
    "org.ow2.asm" % "asm-commons"  % asmVersion,
    "org.ow2.asm" % "asm-tree"     % asmVersion,
    "org.ow2.asm" % "asm-analysis" % asmVersion,
    "org.ow2.asm" % "asm-util"     % asmVersion,

    "org.jgrapht" % "jgrapht-core" % jgraphtVersion,
    "org.jgrapht" % "jgrapht-ext"  % jgraphtVersion,
    "org.jgrapht" % "jgrapht-io"   % jgraphtVersion,

    // NOTE: 3.5.2 has self conflicts in its own dependencies
    "org.apache.maven" % "maven-core"                   % "3.5.4",
    "org.apache.maven" % "maven-compat"                 % "3.5.4",
    "org.apache.maven.indexer" % "indexer-core"         % "6.0.0",
    "org.apache.maven.wagon" % "wagon-http-lightweight" % "3.1.0",

    "org.apache.lucene" % "lucene-core"             % luceneVersion,
    "org.apache.lucene" % "lucene-queryparser"      % luceneVersion,
    "org.apache.lucene" % "lucene-analyzers-common" % luceneVersion,
    "org.apache.lucene" % "lucene-highlighter"      % luceneVersion,
    "org.apache.lucene" % "lucene-backward-codecs"  % luceneVersion
  )
}

// Deal with version conflicts in library dependencies
dependencyOverrides ++= Seq(
  "com.google.guava"    % "guava"        % "26.0-jre",
  "org.codehaus.plexus" % "plexus-utils" % "3.1.0"
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

assemblyShadeRules in assembly := Seq(
  // Conflicts with "javax.annotation" % "javax.annotation-api" % "1.2"
  ShadeRule.rename("javax.annotation.**" -> "javax.annotation.jsr250.@1").inLibrary("javax.annotation" % "jsr250-api" % "1.0")
)

// Create merge strategies that do not cause warnings
def quiet(mergeStrategy: sbtassembly.MergeStrategy): sbtassembly.MergeStrategy = new sbtassembly.MergeStrategy {
  val name = "quiet:" + mergeStrategy.name
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
    mergeStrategy(tempDir, path, files)

  override def notifyThreshold = 1
  override def detailLogLevel: Level.Value = Level.Info
  override def summaryLogLevel: Level.Value = Level.Info
}

// MergeStrategy for `META-INF/plexus/components.xml` files
val componentsXmlMerge: sbtassembly.MergeStrategy = new sbtassembly.MergeStrategy {
  val name = "componentsXmlMerge"
  import scala.xml._

  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] = {
    val components: Seq[Node] =
      files
      .map(XML.loadFile)
      .flatMap(_ \\ "component-set" \ "components" \ "_")
      .flatMap(List(Text("\n    "), _)) ++ Seq(Text("\n  "))
    val componentSet = new Elem(null, "component-set", Null, TopScope, false,
      Text("\n  "),
      new Elem(null, "components", Null, TopScope, false,
        components: _*),
      Text("\n"))

    val file = MergeStrategy.createMergeTarget(tempDir, path)
    XML.save(file.toString, componentSet, enc = "UTF-8", xmlDecl = true)
    IO.append(file, IO.Newline.getBytes(IO.defaultCharset))
    Right(Seq(file -> path))
  }
}

lazy val quietDiscard = quiet(MergeStrategy.discard)
lazy val quietRename = quiet(MergeStrategy.rename)
lazy val quietFilterDistinctLines = quiet(MergeStrategy.filterDistinctLines)
lazy val quietComponentsXmlMerge = quiet(componentsXmlMerge)

assemblyMergeStrategy in assembly := {
  case PathList(file) if List(
    "library.properties", // from scala-library
    "rootdoc.txt",        // from scala-library
    "reflect.properties", // from scala-reflect
    "module-info.class",  // from asm-6.0
    "about.html"          // from org.eclipse.sisu.plexus-0.3.3 and org.eclipse.sisu.inject-0.3.3
  ).contains(file) => quietDiscard

  case PathList("META-INF", file) if List(
    "MANIFEST.MF",
    "DEPENDENCIES",
    "LICENSE",
    "LICENSE.txt",
    "NOTICE",
    "NOTICE.txt"
  ).contains(file) => quietRename

  case PathList("META-INF", "services", xs @ _*)          => quietFilterDistinctLines
  case PathList("META-INF", "plexus", "components.xml")   => quietComponentsXmlMerge
  case PathList("META-INF", "sisu", "javax.inject.Named") => quietFilterDistinctLines

  case _ => MergeStrategy.deduplicate
}
