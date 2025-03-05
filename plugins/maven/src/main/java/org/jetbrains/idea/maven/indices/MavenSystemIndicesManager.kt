// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PathUtilRt
import com.intellij.util.messages.Topic
import com.intellij.util.xmlb.annotations.OptionTag
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.model.MavenIndexId
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.server.MavenIndexUpdateState
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory
import kotlin.io.path.name


interface IndexChangeProgressListener {
  fun indexStatusChanged(state: MavenIndexUpdateState)

}

@Service
@State(name = "MavenIndices", storages = ([Storage(value = "mavenIndicesState.xml", roamingType = RoamingType.LOCAL)]))
@ApiStatus.Experimental
class MavenSystemIndicesManager(val cs: CoroutineScope) : PersistentStateComponent<IndexStateList> {

  private var myState: IndexStateList? = null
  private val luceneIndices = ConcurrentHashMap<String, MavenSearchIndex>()
  private val inMemoryIndices = ConcurrentHashMap<String, MavenGAVIndex>()
  private val gavUpdatingIndixes = ConcurrentHashMap<MavenGAVIndex, Boolean>()
  private val mutex = Mutex()
  private val luceneUpdateStatusMap = ConcurrentHashMap<String, MavenIndexUpdateState>()

  @Volatile
  private var needPoll: Boolean = false


  init {

    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        gc()
      }
    })
  }

  private var ourTestIndicesDir: Path? = null
  suspend fun getClassIndexForRepository(repo: MavenRepositoryInfo): MavenSearchIndex {
    return getIndexForRepo(repo)
  }

  suspend fun getGAVIndexForRepository(repo: MavenRepositoryInfo): MavenGAVIndex? {
    if (repo.kind == RepositoryKind.REMOTE) return null
    val dir = getDirForMavenIndex(repo)
    val result = mutex.withLock {
      inMemoryIndices[dir.toString()]?.let { return@withLock Pair(false, it) }
      MavenLocalGavIndexImpl(repo)
        .also { inMemoryIndices[dir.toString()] = it }
        .let { return@withLock Pair(true, it) }
    }
    if (result.first)
      result.second.also { gavIndex ->
        //IDEA-342984
        val skipUpdate = ApplicationManager.getApplication().isUnitTestMode
                         && Registry.`is`("maven.skip.gav.update.in.unit.test.mode")
        if (!skipUpdate) {
          (gavIndex as? MavenUpdatableIndex)?.let {
            blockingContext {
              scheduleUpdateIndexContent(listOf(gavIndex), false)
            }
          }
        }
      }
    return result.second
  }


  @TestOnly
  fun setTestIndicesDir(myTestIndicesDir: Path?) {
    ourTestIndicesDir = myTestIndicesDir
  }

  fun getIndicesDir(): Path {
    return ourTestIndicesDir ?: MavenUtil.getPluginSystemDir("Indices")
  }

  private suspend fun getIndexForRepo(repo: MavenRepositoryInfo): MavenSearchIndex {
    return cs.async(Dispatchers.IO) {
      val dir = getDirForMavenIndex(repo)
      mutex.withLock {
        luceneIndices[dir.toString()]?.let { return@async it }

        val indexId = MavenIndexId(
          dir.name, repo.id, if (repo.kind == RepositoryKind.LOCAL) repo.url else null,
          if (repo.kind == RepositoryKind.REMOTE) repo.url else null, dir.absolutePathString()
        )
        return@async MavenLuceneClassIndexServer(repo, indexId, getIndexWrapper()).also {
          luceneIndices[dir.toString()] = it

          getOrCreateState().mavenIndicesData.computeIfAbsent(repo.url) {
            IndexStateList.MavenIndexData().also {
              it.id = indexId.indexId
              it.repoId = repo.id
              it.datadir = dir.toString()
              it.url = repo.url
              it.timestamp = -1
            }
          }
        }
      }
    }.await()
  }

  private fun getOrCreateState(): IndexStateList {
    var state = myState
    if (state == null) {
      state = IndexStateList()
      myState = state
    }
    return state
  }

  private fun getIndexProperty(dir: Path,
                               repo: MavenRepositoryInfo): MavenIndexUtils.IndexPropertyHolder {
    val holder = getProperties(dir) ?: MavenIndexUtils.IndexPropertyHolder(
      dir,
      repo.kind,
      setOf(repo.id),
      repo.url
    )
    return holder
  }


  private fun getProperties(dir: Path): MavenIndexUtils.IndexPropertyHolder? {
    try {
      return MavenIndexUtils.readIndexProperty(dir)
    }
    catch (e: MavenIndexException) {
      MavenLog.LOG.warn(e)
      return null
    }

  }

  private fun getIndexWrapper(): MavenIndexerWrapper {
    return MavenServerManager.getInstance().createIndexer()
  }

  private fun getDirForMavenIndex(repo: MavenRepositoryInfo): Path {
    val url = getCanonicalUrl(repo)
    val key = PathUtilRt.suggestFileName(PathUtilRt.getFileName(url), false, false)

    val locationHash = Integer.toHexString((url).hashCode())
    return getIndicesDir().resolve("$key-$locationHash")
  }


  private fun getCanonicalUrl(repo: MavenRepositoryInfo): String {
    if (Path.of(repo.url).isDirectory()) return Path.of(repo.url).toCanonicalPath()
    try {
      val uri = URI(repo.url)
      if (uri.scheme == null || uri.scheme.lowercase() == "file") {
        val path = uri.path
        if (path != null) return path
      }
      return uri.toString()
    }
    catch (e: URISyntaxException) {
      return repo.url
    }

  }


  fun getUpdatingStateSync(project: Project, repository: MavenRepositoryInfo): IndexUpdatingState {
    val status = luceneUpdateStatusMap[repository.url]
    if (status == null) return IndexUpdatingState.IDLE else return IndexUpdatingState.UPDATING
  }

  fun gc() {
    val validIndices = ReadAction.compute<Set<Any>, Throwable> {
      val existed = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
      getOpenedProjects()
        .filter { !it.isDisposed }
        .mapNotNull { MavenIndicesManager.getInstanceIfCreated(it) }
        .forEach {
          existed.addAll(it.getGAVIndices())
        }
      existed
    }


    collectGarbage(validIndices, luceneIndices) {
      it.close(true)
    }
    collectGarbage(validIndices, inMemoryIndices) {
      it.close(true)
    }

  }

  private fun <T : Any> collectGarbage(validIndices: Set<Any>, indices: MutableMap<String, T>, action: (T) -> Unit = {}) {
    val iterator = indices.iterator()
    while (iterator.hasNext()) {
      val idx = iterator.next()
      if (!validIndices.contains(idx.value)) {
        iterator.remove()
        action(idx.value)
      }
    }
  }

  fun scheduleUpdateIndexContent(toUpdate: List<MavenUpdatableIndex>, explicit: Boolean) {
    val luceneUpdate = ArrayList<MavenLuceneClassIndexServer>()
    val inMemoryUpdate = ArrayList<MavenGAVIndex>()
    for (idx: MavenUpdatableIndex in toUpdate) {
      if (idx is MavenLuceneClassIndexServer && idx in luceneIndices.values) luceneUpdate.add(idx)
      else if (idx is MavenGAVIndex && idx in inMemoryIndices.values
               && gavUpdatingIndixes.putIfAbsent(idx, true) == null) inMemoryUpdate.add(idx)
    }

    inMemoryUpdate.forEach { idx ->
      cs.launchTracked(Dispatchers.IO) {
        MavenLog.LOG.info("Starting update maven index for ${idx.repository}")
        val indicator = MavenProgressIndicator(null, null)
        try {
          (idx as MavenUpdatableIndex).update(indicator, explicit)
        }
        catch (ignore: MavenProcessCanceledException) {
        }
        finally {
          gavUpdatingIndixes.remove(idx)
        }
      }
    }

    luceneUpdate.forEach { idx ->
      luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(idx.repository.url, null, null,
                                                                        MavenIndexUpdateState.State.INDEXING)
      cs.launchTracked {
        try {
          val indicator = MavenProgressIndicator(null, null)
          idx.update(indicator, explicit)
          getOrCreateState().updateTimestamp(idx.repository)
          luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(
            idx.repository.url, null, null,
            MavenIndexUpdateState.State.SUCCEED)

        }
        catch (e: Throwable) {
          MavenLog.LOG.error(e)
          luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(
            idx.repository.url, null, null,
            MavenIndexUpdateState.State.FAILED)
        }

      }
    }
  }

  @TestOnly
  suspend fun waitAllGavsUpdatesCompleted() {
    while (!gavUpdatingIndixes.isEmpty()) {
      delay(500)
    }
  }

  @TestOnly
  suspend fun waitAllLuceneUpdatesCompleted() {
    while (true) {
      delay(500)
      if (luceneUpdateStatusMap.isEmpty() || luceneUpdateStatusMap.values.all {
          it.myState == MavenIndexUpdateState.State.SUCCEED
          || it.myState == MavenIndexUpdateState.State.FAILED
        }) return
    }
  }

  @TestOnly
  fun getAllGavIndices(): List<MavenGAVIndex> {
    return java.util.List.copyOf(inMemoryIndices.values)
  }

  fun updateIndexContent(repositoryInfo: MavenRepositoryInfo, project: Project) {
    cs.launch(Dispatchers.IO) {
      withBackgroundProgress(project, IndicesBundle.message("maven.indices.updating.for.repo", repositoryInfo.url), TaskCancellation.cancellable()) {
        val mavenIndex = getClassIndexForRepository(repositoryInfo)
        blockingContext {
          ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(
            MavenIndexUpdateState(repositoryInfo.url,
                                  null,
                                  IndicesBundle.message("maven.indices.updating.for.repo", repositoryInfo.url),
                                  MavenIndexUpdateState.State.INDEXING))

          blockingContextToIndicator {
            val indicator = MavenProgressIndicator(null, ProgressManager.getInstance().progressIndicator, null)
            try {
              (mavenIndex as? MavenUpdatableIndex)?.updateOrRepair(true, indicator, true)
              ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(
                MavenIndexUpdateState(repositoryInfo.url,
                                      null,
                                      IndicesBundle.message("maven.indices.updated.for.repo", repositoryInfo.url),
                                      MavenIndexUpdateState.State.SUCCEED))
            }
            catch (e: MavenProcessCanceledException) {
              ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(
                MavenIndexUpdateState(repositoryInfo.url,
                                      e.message,
                                      IndicesBundle.message("maven.indices.updated.for.repo", repositoryInfo.url),
                                      MavenIndexUpdateState.State.CANCELLED))
            }
            catch (e: Throwable) {
              if (e !is ProcessCanceledException) {
                MavenLog.LOG.warn(e)
              }
              ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(
                MavenIndexUpdateState(repositoryInfo.url,
                                      e.message,
                                      IndicesBundle.message("maven.index.updated.error"),
                                      MavenIndexUpdateState.State.FAILED))
            }

          }
        }
      }
    }

  }

  companion object {

    @JvmStatic
    fun getInstance(): MavenSystemIndicesManager = ApplicationManager.getApplication().service()


    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<IndexChangeProgressListener> = Topic("indexChangeProgressListener", IndexChangeProgressListener::class.java)
  }

  override fun getState(): IndexStateList? {
    return myState
  }

  override fun loadState(state: IndexStateList) {
    myState = state
  }
}

class IndexStateList : BaseState() {
  @get:OptionTag("MAVEN_INDICES_DATA")
  val mavenIndicesData by map<String, MavenIndexData>()

  fun updateTimestamp(repo: MavenRepositoryInfo) {
    val mavenIndexData = mavenIndicesData[repo.url]
    if (mavenIndexData != null) {
      mavenIndexData.timestamp = System.currentTimeMillis()
      incrementModificationCount()
    }
  }

  class MavenIndexData : BaseState() {
    @get:OptionTag("id")
    var id by string()

    @get:OptionTag("repoId")
    var repoId by string()

    @get:OptionTag("url")
    var url by string()

    @get:OptionTag("dataDir")
    var datadir by string()

    @get:OptionTag("updateTimestampe")
    var timestamp by property(-1L)

  }

  companion object {

  }
}

