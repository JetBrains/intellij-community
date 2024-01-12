// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.messages.Topic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.indices.MavenSearchIndex.IndexListener
import org.jetbrains.idea.maven.model.*
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectChanges
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.jetbrains.idea.maven.server.MavenServerConnector
import org.jetbrains.idea.maven.server.MavenServerDownloadListener
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder
import org.jetbrains.idea.maven.statistics.MavenIndexUsageCollector
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Main api class for work with maven indices.
 *
 *
 * Get current index state, schedule update index list, check MavenId in index, add data to index.
 */
@Service(Service.Level.PROJECT)
class MavenIndicesManager(private val myProject: Project, private val cs: CoroutineScope) : Disposable {
  interface MavenIndexerListener {
    fun gavIndexUpdated(repo: MavenRepositoryInfo, added: Set<File>, failedToAdd: Set<File>) {}
    fun classIndexUpdated(repo: MavenRepositoryInfo, added: Set<File>, failedToAdd: Set<File>) {}
  }

  private val mySearchIndices = CopyOnWriteArrayList<MavenSearchIndex>();
  private val myGavIndices = CopyOnWriteArrayList<MavenGAVIndex>();

  private val myDownloadListener = MavenIndexServerDownloadListener(this)
  private val localMavenGavIndex: MavenGAVIndex?
    get() {
      return myGavIndices.firstOrNull { it.repository.kind == RepositoryKind.LOCAL }
    }


  init {
    initListeners()
  }

  override fun dispose() {
    deleteIndicesDirInUnitTests()
  }

  private fun deleteIndicesDirInUnitTests() {
    if (MavenUtil.isMavenUnitTestModeEnabled()) {
      FileUtil.deleteRecursively(MavenSystemIndicesManager.getInstance().getIndicesDir())
    }
  }

  fun getCommonGavIndex(): MavenGAVIndex {
    return MavenGAVIndexWrapper();
  }

  fun updateIndicesListSync() {
    scheduleUpdateIndicesList()
  }

  val isInit: Boolean
    get() = true

  private fun initListeners() {
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(MavenServerConnector.DOWNLOAD_LISTENER_TOPIC, myDownloadListener)

    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(MavenSearchIndex.INDEX_IS_BROKEN, MavenSearchIndexListener(this))

    MavenRepositoryProvider.EP_NAME.addChangeListener({ scheduleUpdateIndicesList() }, this)
    val projectsManager = MavenProjectsManager.getInstance(myProject)

    projectsManager.addProjectsTreeListener(object : MavenProjectsTree.Listener {
      override fun projectResolved(projectWithChanges: Pair<MavenProject, MavenProjectChanges>,
                                   nativeMavenProject: NativeMavenProjectHolder?) {
        scheduleUpdateIndicesList();
      }
    }, this)
  }

  fun scheduleUpdateIndicesList() {
    scheduleUpdateIndicesList() {}
  }


  fun scheduleUpdateIndicesList(onUpdate: () -> Unit) {
    cs.launch(Dispatchers.IO) {
      updateIndexList()
      onUpdate()
    }
  }


  suspend fun updateIndexList() {
    try {
      myGavIndices.clear()
      mySearchIndices.clear();
      MavenIndexUtils.getLocalRepository(myProject)?.let {
        myGavIndices.add(MavenSystemIndicesManager.getInstance().getGAVIndexForRepository(it))
        mySearchIndices.add(MavenSystemIndicesManager.getInstance().getClassIndexForRepository(it))
      }

      if (MavenProjectsManager.getInstanceIfCreated(myProject)?.isMavenizedProject == true) {
        val repositories = MavenIndexUtils.getRemoteRepositoriesNoResolve(myProject);


        mySearchIndices.addAll(repositories.mapNotNull {
          MavenSystemIndicesManager.getInstance().getClassIndexForRepository(
            MavenRepositoryInfo(it.id, it.name, it.url, RepositoryKind.REMOTE))
        })

      }
    }
    catch (e: Exception) {
      MavenLog.LOG.error(e)
    }
    finally {
      MavenSystemIndicesManager.getInstance().gc()
    }
  }

  fun hasLocalGroupId(groupId: String?): Boolean {
    val localIndex = localMavenGavIndex
    return localIndex != null && groupId != null && localIndex.hasGroupId(groupId)
  }

  fun hasLocalArtifactId(groupId: String?, artifactId: String?): Boolean {
    val localIndex = localMavenGavIndex
    return localIndex != null && groupId != null && artifactId != null && localIndex.hasArtifactId(groupId, artifactId)
  }

  fun hasLocalVersion(groupId: String?, artifactId: String?, version: String?): Boolean {
    val localIndex = localMavenGavIndex
    return localIndex != null && groupId != null && artifactId != null && version != null && localIndex.hasVersion(groupId, artifactId,
                                                                                                                   version)
  }

  /**
   * Add artifact info to index async.
   */
  fun scheduleArtifactIndexing(mavenId: MavenId?, artifactFile: File, localRepo: String): Boolean {
    try {
      val localIndex = myGavIndices.firstOrNull {
        it.repository.kind == RepositoryKind.LOCAL && it.repository.url == localRepo
      }
      if (localIndex == null) {
        return false
      }
      if (mavenId != null) {
        val groupId = mavenId.groupId
        val artifactId = mavenId.artifactId
        val version = mavenId.version
        if (groupId == null || artifactId == null || version == null) {
          return false
        }
        if (localIndex.hasVersion(groupId, artifactId, version)) return false
      }


      fixIndex(artifactFile)
    }
    catch (ignore: AlreadyDisposedException) {
      return false
    }
    return true
  }

  fun scheduleUpdateLocalGavContent(explicit: Boolean) {
    val localIndex = localMavenGavIndex;
    if (localIndex is MavenUpdatableIndex) {
      MavenSystemIndicesManager.getInstance().scheduleUpdateIndexContent(listOf(localIndex), explicit)
    }
  }

  /**
   * Schedule update local gav index content async.
   */
  fun scheduleUpdateContentLocalGav(explicit: Boolean) {
    val localIndex = localMavenGavIndex;
    if (localIndex is MavenUpdatableIndex) {
      MavenSystemIndicesManager.getInstance().scheduleUpdateIndexContent(listOf(localIndex), explicit)
    }
  }

  fun scheduleUpdateContentAll(explicit: Boolean) {
    val toUpdate = ArrayList<MavenUpdatableIndex>()
    val localIndex = localMavenGavIndex;
    if (localIndex is MavenUpdatableIndex) {
      toUpdate.add(localIndex)
    }
    toUpdate.addAll(mySearchIndices.filterIsInstance<MavenUpdatableIndex>())
    MavenSystemIndicesManager.getInstance().scheduleUpdateIndexContent(toUpdate, explicit)
  }

  fun scheduleUpdateContentLocalClassIndex(explicit: Boolean) {
    val indexLocal = mySearchIndices
      .filter { it.repository.kind == RepositoryKind.LOCAL }
      .filterIsInstance<MavenUpdatableIndex>()
    if (indexLocal.isNotEmpty()) {
      MavenSystemIndicesManager.getInstance().scheduleUpdateIndexContent(indexLocal, explicit)
    }

  }

  fun searchForClass(patternForQuery: String?): Set<MavenArtifactInfo> {
    return mySearchIndices
      .flatMap { it.search(patternForQuery, 50).asSequence() }
      .toSet()
  }


  private fun fixIndex(artifactFile: File) {
    MavenIndexUsageCollector.ADD_ARTIFACT_FROM_POM.log(myProject)

    val localGavIndex = myGavIndices.filter {
      it.repository.kind == RepositoryKind.LOCAL
    }.filterIsInstance<MavenUpdatableIndex>()
      .firstOrNull()
    val localLuceneIndex = mySearchIndices.filter {
      it.repository.kind == RepositoryKind.LOCAL
    }.filterIsInstance<MavenUpdatableIndex>()
      .firstOrNull()

    localGavIndex?.let { addToIndexAndNotify(it, artifactFile) { repo, added, failed -> gavIndexUpdated(repo, added, failed) } }
  }

  private fun addToIndexAndNotify(index: MavenUpdatableIndex,
                                  artifactFile: File,
                                  action: MavenIndexerListener.(MavenRepositoryInfo, Set<File>, Set<File>) -> Unit) {
    cs.launch(Dispatchers.IO) {
      val artifactResponses = index.tryAddArtifacts(listOf(artifactFile))
      ApplicationManager.getApplication().messageBus.syncPublisher(INDEXER_TOPIC)
        .action(index.repository,
                artifactResponses.filter { it.indexedMavenId() != null }.map { it.artifactFile() }.toSet(),
                artifactResponses.filter { it.indexedMavenId() == null }.map { it.artifactFile() }.toSet()
        )
    }
  }


  internal fun getGAVIndices(): List<MavenGAVIndex> {
    return ArrayList(myGavIndices)
  }

  internal fun getSearchIndices(): List<MavenSearchIndex> {
    return ArrayList(mySearchIndices)
  }

  private class MavenIndexServerDownloadListener(private val myManager: MavenIndicesManager) : MavenServerDownloadListener {
    override fun artifactDownloaded(file: File, relativePath: String) {
      val localRepository = MavenIndexUtils.getLocalRepository(myManager.myProject)
      localRepository?.url?.let { myManager.scheduleArtifactIndexing(null, file, it) }
    }
  }

  private class MavenSearchIndexListener(private val myManager: MavenIndicesManager) : IndexListener {
    override fun indexIsBroken(index: MavenSearchIndex) {
      if (index is MavenUpdatableIndex) {
        MavenSystemIndicesManager.getInstance().scheduleUpdateIndexContent(listOf(index), false)
      }
    }
  }

  @TestOnly
  fun waitForGavUpdateCompleted() {
    runBlocking {
      MavenSystemIndicesManager.getInstance().waitAllGavsUpdatesCompleted()
    }
  }

  @TestOnly
  fun waitForLuceneUpdateCompleted() {
    runBlocking {
      MavenSystemIndicesManager.getInstance().waitAllLuceneUpdatesCompleted()
    }

  }


  companion object {
    @Topic.AppLevel
    @JvmField
    val INDEXER_TOPIC: Topic<MavenIndexerListener> = Topic(
      MavenIndexerListener::class.java.simpleName, MavenIndexerListener::class.java)

    @JvmStatic
    fun getInstance(project: Project): MavenIndicesManager {
      return project.getService(MavenIndicesManager::class.java)
    }

    @JvmStatic
    fun getInstanceIfCreated(project: Project): MavenIndicesManager? {
      return project.getServiceIfCreated(MavenIndicesManager::class.java)
    }

    @JvmStatic
    fun addArchetype(archetype: MavenArchetype) {
      MavenArchetypeManager.addArchetype(archetype, userArchetypesFile)
    }

    private val userArchetypesFile: Path
      get() = MavenSystemIndicesManager.getInstance().getIndicesDir().resolve("UserArchetypes.xml")
  }

  inner class MavenGAVIndexWrapper : MavenGAVIndex {
    override fun close(releaseIndexContext: Boolean) {
    }

    override fun getGroupIds(): Collection<String> {
      return myGavIndices.flatMap { it.groupIds }
    }

    override fun getArtifactIds(groupId: String): Set<String> {
      return myGavIndices.flatMap { it.getArtifactIds(groupId) }.toSet()
    }

    override fun getVersions(groupId: String, artifactId: String): Set<String> {
      return myGavIndices.flatMap { it.getVersions(groupId, artifactId) }.toSet()
    }

    override fun hasGroupId(groupId: String): Boolean {
      return myGavIndices.any { it.hasGroupId(groupId) }
    }

    override fun hasArtifactId(groupId: String, artifactId: String): Boolean {
      return myGavIndices.any { it.hasArtifactId(groupId, artifactId) }
    }

    override fun hasVersion(groupId: String, artifactId: String, version: String): Boolean {
      return myGavIndices.any { it.hasVersion(groupId, artifactId, version) }
    }

    override fun getKind(): RepositoryKind {
      throw IllegalStateException("Internal API, do not use it")
    }

    override fun getRepository(): MavenRepositoryInfo {
      throw IllegalStateException("Internal API, do not use it")
    }

  }
}
