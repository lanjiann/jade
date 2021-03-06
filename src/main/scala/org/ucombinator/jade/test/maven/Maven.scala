package org.ucombinator.jade.test.maven

import java.io.File
import java.net.{URI, URLEncoder}

import org.apache.lucene.index.MultiFields
import org.apache.maven.index.Indexer
import org.apache.maven.index.context.{IndexCreator, IndexUtils, IndexingContext}
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator
import org.apache.maven.index.updater.{IndexUpdateRequest, IndexUpdater, WagonHelper}
import org.apache.maven.wagon.Wagon
import org.apache.maven.wagon.events.TransferEvent
import org.apache.maven.wagon.observers.AbstractTransferListener
import org.codehaus.plexus.{DefaultContainerConfiguration, DefaultPlexusContainer, PlexusConstants}

import scala.collection.JavaConverters._

object Maven {
  val config = {
    val c = new DefaultContainerConfiguration
    c.setClassPathScanning(PlexusConstants.SCANNING_INDEX)
  }
  val plexusContainer = new DefaultPlexusContainer(config)

  val indexer = plexusContainer.lookup(classOf[Indexer])
  val indexUpdater = plexusContainer.lookup(classOf[IndexUpdater])

  // See https://maven.apache.org/maven-indexer/indexer-core/apidocs/index.html?constant-values.html
  val indexers = List(
    plexusContainer.lookup(classOf[IndexCreator], MinimalArtifactInfoIndexCreator.ID))

  val listener = new AbstractTransferListener() {
    override def transferStarted(transferEvent: TransferEvent): Unit = { println(f"  Downloading ${transferEvent.getWagon.getRepository.getUrl}/${transferEvent.getResource.getName}") }
    override def transferCompleted(transferEvent: TransferEvent): Unit = { println("   - Done") }
  }

  def getContext(indexesDir: File, url: URI): IndexingContext = {
    val subdir = new File(indexesDir, URLEncoder.encode(url.toString, "UTF-8"))
    val cache = new File(subdir, "cache")
    val index = new File(subdir, "index")
    cache.mkdirs()
    index.mkdirs()

    indexer.createIndexingContext("context-id", "repository-id", cache, index, url.toString, null, true, true, indexers.asJava)
  }

  def updateIndexes(indexesDir: File, urls0: List[String]): Unit = {
    val urls = if (urls0.isEmpty) { MavenRepositories.repositories.values.toList } else { urls0 }

    for ((url: java.net.URI, i) <- urls.zipWithIndex) {
      println(f"Updating (${i + 1} of ${urls.length}): $url")

      try {
        val context = getContext(indexesDir, url)

        val timestamp = context.getTimestamp
        val httpWagon = plexusContainer.lookup(classOf[Wagon], "http")
        val resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, listener, null, null)
        val updateResult = indexUpdater.fetchAndUpdateIndex(new IndexUpdateRequest(context, resourceFetcher))

        if (updateResult.isFullUpdate) { println("Full update completed") }
        else if (updateResult.getTimestamp == timestamp) { println("No update needed") }
        else { println(f"Incremental update completed ($timestamp through ${updateResult.getTimestamp})") }
      } catch {
        case e: Throwable =>
          println("Update failed")
          e.printStackTrace()
      }

      println()
    }
  }

  def listArtifacts(indexesDir: File, urls0: List[String]): Unit = {
    val urls = if (urls0.isEmpty) { MavenRepositories.repositories.values.toList } else { urls0 }

    for ((url: java.net.URI, i) <- urls.zipWithIndex) {
      println(f"Listing (${i + 1} of ${urls.length}): $url")

      try {
        val context = getContext(indexesDir, url)

      val searcher = context.acquireIndexSearcher
      try {
        val ir = searcher.getIndexReader
        val liveDocs = MultiFields.getLiveDocs(ir)
        var i = 0
        while ( {
          i < ir.maxDoc
        }) {
          if (liveDocs == null || liveDocs.get(i)) {
            val doc = ir.document(i)
            val ai = IndexUtils.constructArtifactInfo(doc, context)
            //System.out.println(ai.getGroupId + ":" + ai.getArtifactId + ":" + ai.getVersion + ":" + ai.getClassifier + " (sha1=" + ai.getSha1 + ")")
            println("gav:"+ai.calculateGav())
            println("fn:"+ai.getFileName)
            println("rep:"+ai.getRepository)
            println("path:"+ai.getPath)
            println("url"+ai.getRemoteUrl);

            println("ai"+ai)
            for (d <- doc.iterator().asScala) {
              println("field: " + d.fieldType() + ":" + d.name() + ":" + d.stringValue() + ":" + d)
            }
            println()
          }

          {
            i += 1; i - 1
          }
        }
      } finally {
          context.releaseIndexSearcher(searcher)
      }
      } catch {
        case e: Throwable =>
          println("Update failed")
          e.printStackTrace()
      }

      println()
    }
  }

  def testArtifact(): Unit = {
    // Files where local cache is (if any) and Lucene Index should be located
    val centralLocalCache = new File("maven-cache/central-cache")
    val centralIndexDir = new File("maven-cache/central-index")

    // Create context for central repository index
    val centralContext = indexer.createIndexingContext("central-context", "central", centralLocalCache, centralIndexDir, "http://repo1.maven.org/maven2", null, true, true, indexers.asJava)

    // Update the index (incremental update will happen if this is not 1st run and files are not deleted)
    // This whole block below should not be executed on every app start, but rather controlled by some configuration
    // since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
    // other index sources might have different index publishing frequency.
    // Preferred frequency is once a week.
    if (true) {
      System.out.println("Updating Index...")
      System.out.println("This might take a while on first run, so please be patient!")
      // Create ResourceFetcher implementation to be used with IndexUpdateRequest
      // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
      System.out.println()
    }

    System.out.println()
    System.out.println("Using index")
    System.out.println("===========")
    System.out.println()

    // ====
    // Case:
    // dump all the GAVs
    // NOTE: will not actually execute do this below, is too long to do (Central is HUGE), but is here as code
    // example
    if (true) {
      val searcher = centralContext.acquireIndexSearcher
      try {
        val ir = searcher.getIndexReader
        val liveDocs = MultiFields.getLiveDocs(ir)
        var i = 0
        while ( {
          i < ir.maxDoc
        }) {
          if (liveDocs == null || liveDocs.get(i)) {
            val doc = ir.document(i)
            val ai = IndexUtils.constructArtifactInfo(doc, centralContext)
            //System.out.println(ai.getGroupId + ":" + ai.getArtifactId + ":" + ai.getVersion + ":" + ai.getClassifier + " (sha1=" + ai.getSha1 + ")")
            println("gav:"+ai.calculateGav())
            println("fn:"+ai.getFileName)
            println("rep:"+ai.getRepository)
            println("path:"+ai.getPath)
            println("url"+ai.getRemoteUrl);

            println("ai"+ai)
            for (d <- doc.iterator().asScala) {
              println("field: " + d.fieldType() + ":" + d.name() + ":" + d.stringValue() + ":" + d)
            }
          }

          {
            i += 1; i - 1
          }
        }
      } finally centralContext.releaseIndexSearcher(searcher)
    }

//org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
  /*
    //org.apache.maven.artifact.repository.metadata.RepositoryMetadataManager
    //ContainerConfiguration
    //org.apache.maven.artifact.repository.metadata.DefaultRepositoryMetadataManager
    //org.apache.maven.repository.legacy.DefaultWagonManager
    val updateCheckManager = new org.apache.maven.repository.legacy.DefaultUpdateCheckManager()
    val container = new DefaultPlexusContainer()
    //container.addComponent(updateCheckManager, "org.apache.maven.repository.legacy.UpdateCheckManager")
    //val artifactMetadataSource = container.lookup(classOf[ArtifactMetadataSource])
    //org.codehaus.plexus.classworlds.strategy.SelfFirstStrategy
    org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader

    //new DefaultRepositoryLayout()
    val snapshotPolicy = new ArtifactRepositoryPolicy(false, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL)
    val releasesPolicy = new ArtifactRepositoryPolicy(false, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS, ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL)
    val r = new MavenArtifactRepository(repoUrl(), repoUrl(), new DefaultRepositoryLayout(), snapshotPolicy, releasesPolicy)
    //r.pathOfRemoteRepositoryMetadata()
    val artifact = new DefaultArtifact(groupId(), artifactId(), "6.0.0", null, "jar", null, new DefaultArtifactHandler())
    val artifactMetadata = new ArtifactRepositoryMetadata(artifact)
    println("path: " + r.pathOfRemoteRepositoryMetadata(artifactMetadata))
    //Artifact.LATEST_VERSION
    //AbstractRepositoryMetadata
    //new DefaultMetadata
    //DefaultRepositoryRequest
    //Versioning
    //ArtifactMetadataSource
    //val metadataSource = new DefaultMetadataSource()
    //ArtifactRepository
    //val versions = artifactMetadataSource.retrieveAvailableVersions(artifact, null, List(r: ArtifactRepository).asJava)
    //val versions = r.findVersions(artifact)
    //r.find()
    //new DefaultArtifact(groupId: String, artifactId: String, versionRange: VersionRange, scope: String, `type`: String, classifier: String, artifactHandler: ArtifactHandler, optional: Boolean)
    //println(versions.asScala)
    */
  }
}
