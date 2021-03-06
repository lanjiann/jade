package org.ucombinator.jade

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.jar.{JarEntry, JarFile}

import scala.collection.JavaConverters._
import org.apache.commons.io.FileUtils
import org.rogach.scallop.{ScallopConf, ScallopOption}
import org.ucombinator.jade.test.maven.Maven

object Main extends App {
  val conf: Main = new Main(this.args)

  conf.subcommand match {
    case None => conf.errorMessageHandler("Missing subcommand")
    case Some(m: JadeSubcommand) => m.run()
    case Some(m) => conf.errorMessageHandler("Unknown subcommand: " + m)
  }
}

class Main(args: Seq[String]) extends ScallopConf(args = args) with JadeScallopConf {
  shortSubcommandsHelp(true)

  banner("Usage: jade [subcommand] [options]")
  addSubcommand(DecompileClass)
  addSubcommand(Decompile)
  addSubcommand(UpdateIndexes)
  addSubcommand(ListArtifacts)
  addSubcommand(TestArtifact)
  verify()
}

object DecompileClass extends JadeSubcommand("decompile-class") {
  val className: ScallopOption[String] = trailArg[String]()

  override def run(): Unit = {
      DecompileOneClass.decompileOne(className())
  }
}


object Decompile extends JadeSubcommand("decompile") {
  val jarFile = trailArg[String]()
  val destinationFolder = trailArg[String]()

  override def run(): Unit = {
    // TODO: accept a directory that includes .class files
    val appName: String = jarFile()
    val destinationDirectoryName: String = destinationFolder()
    validityCheck(appName, destinationDirectoryName)

    val projectDirectory: Path = {
      val appBaseName = appName.split("/").last.stripSuffix(".jar")

      Paths.
        get(destinationDirectoryName, appBaseName).
        toAbsolutePath
    }

    createProjectDirectory(projectDirectory.toFile)
    val jar = new JarFile(appName)
    processJar(jar, projectDirectory)
  }


  private def validityCheck(appName: String, destinationDirectoryName: String)
  : Unit = {
    require(appName.endsWith(".jar"), "The first argument is NOT a .jar file!")

    val app = new File(appName)

    require(app.exists, s"The $appName file CANNOT be found!")
    require(app.canRead, s"The $appName file CANNOT be read!")

    val destinationDirectory = new File(destinationDirectoryName)

    require(destinationDirectory.exists, "The given destination (the second argument) does NOT exist!")
    require(destinationDirectory.isDirectory, "The given destination (the second argument) is NOT a directory!")
    require(destinationDirectory.canWrite, "The given destination directory (the second argument) is NOT writable!")
  }

  private def createProjectDirectory(directory: File)
  : Unit = {
    // TODO: Ask the user to decide what to do about this exist same name folder
    if (directory.exists) { FileUtils.deleteDirectory(directory) }
    assert(directory.mkdir(), s"CANNOT create folder $directory!")
  }

  private def processJar(jarFile: JarFile, projectDirectory: Path)
  : Unit = {
    val (directories, files) = jarFile.entries.asScala.partition(_.isDirectory)
    cloneJarFileDirectoryStructure(directories, projectDirectory)

    val (classFiles, nonClassFiles) = files.partition(_.getName.endsWith(".class"))
    cloneNonClassFilesInJar(jarFile, nonClassFiles, projectDirectory.toString)

    val decompiler: Decompiler = new Decompiler(jarFile, classFiles)
    decompiler.decompileToFiles()
  }

  private def cloneJarFileDirectoryStructure(directories: Iterator[JarEntry], outputDirectory: Path)
  : Unit =
    for (d <- directories) {
      // TODO: Create this annotation
      // @CanIgnoreReturnValue
      Files.createDirectory(Paths.get(outputDirectory.toString, d.getName))
    }

  private def cloneNonClassFilesInJar(jarFile: JarFile, nonClassFiles: Iterator[JarEntry], outputDirectoryName: String)
  : Unit =
      for (d <- nonClassFiles;
           path = Paths.get(outputDirectoryName, d.getName)) {
        Files.copy(jarFile.getInputStream(d), path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
}

object UpdateIndexes extends JadeSubcommand("update-indexes") {
  val indexDir = opt[java.io.File](default = Some(new java.io.File("maven-indexes")))
  val url = trailArg[List[String]](default = Some(List()))

  def run(): Unit = { Maven.updateIndexes(indexDir(), url()) }
}

object ListArtifacts extends JadeSubcommand("list-artifacts") {
  val indexDir = opt[java.io.File](default = Some(new java.io.File("maven-indexes")))
  val url = trailArg[List[String]](default = Some(List()))

  def run(): Unit = { Maven.listArtifacts(indexDir(), url()) }
}

object TestArtifact extends JadeSubcommand("test-artifact") {
  //val repoUrl = trailArg[String]()
  //val groupId = trailArg[String]()
  //val artifactId = trailArg[String]()

  def run(): Unit = { Maven.testArtifact() }
}
