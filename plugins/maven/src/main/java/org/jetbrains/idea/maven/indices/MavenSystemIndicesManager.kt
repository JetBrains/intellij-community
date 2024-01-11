// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.util.PathUtilRt
import com.intellij.util.messages.Topic
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly
import org.jetbrains.idea.maven.model.MavenRepositoryInfo
import org.jetbrains.idea.maven.model.RepositoryKind
import org.jetbrains.idea.maven.server.MavenIndexUpdateState
import org.jetbrains.idea.maven.server.MavenIndexerWrapper
import org.jetbrains.idea.maven.server.MavenServerManager
import org.jetbrains.idea.maven.utils.MavenLog
import org.jetbrains.idea.maven.utils.MavenProgressIndicator
import org.jetbrains.idea.maven.utils.MavenUtil
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap


interface IndexChangeProgressListener {
  fun indexStatusChanged(state: MavenIndexUpdateState)

}

@Service
class MavenSystemIndicesManager(val cs: CoroutineScope) {


  private val luceneIndices = ConcurrentHashMap<String, MavenIndex>()
  private val inMemoryIndices = ConcurrentHashMap<String, MavenGAVIndex>()
  private val gavUpdatingIndixes = ConcurrentHashMap<MavenGAVIndex, Boolean>()
  private val mutex = Mutex()
  private val luceneUpdateStatusMap = ConcurrentHashMap<String, MavenIndexUpdateState>()

  @Volatile
  private var needPoll: Boolean = false


  init {
    cs.launch {
      while (isActive) {
        delay(2000)
        val statusToSend = ArrayList<MavenIndexUpdateState>();
        if (!needPoll) continue
        var anyInProgress = false;
        status().forEach { s ->
          anyInProgress = anyInProgress || s.myState == MavenIndexUpdateState.State.INDEXING
          val oldStatus = luceneUpdateStatusMap[s.myUrl];
          if (oldStatus == null || oldStatus.timestamp < s.timestamp) {
            statusToSend.add(s)
            luceneUpdateStatusMap[s.myUrl] = s
          }
        }

        statusToSend.forEach {
          ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(it)
        }
        needPoll = anyInProgress
      }

    }

    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        gc()
      }
    });
  }

  private var ourTestIndicesDir: Path? = null
  suspend fun getClassIndexForRepository(repo: MavenRepositoryInfo): MavenSearchIndex? {
    return getIndexForRepo(repo) as? MavenSearchIndex ?: null
  }

  suspend fun getGAVIndexForRepository(repo: MavenRepositoryInfo): MavenGAVIndex? {
    if (repo.kind == RepositoryKind.REMOTE) return null
    return cs.async(Dispatchers.IO) {
      val dir = getDirForMavenIndex(repo)
      mutex.withLock {
        inMemoryIndices[dir.toString()]?.let { return@async it }
        return@async MavenLocalGavIndexImpl(repo)
          .also { inMemoryIndices[dir.toString()] = it }
          .also { scheduleUpdateIndexContent(listOf(it), false) }
      }
    }.await()
  }

  @TestOnly
  fun setTestIndicesDir(myTestIndicesDir: Path?) {
    ourTestIndicesDir = myTestIndicesDir
  }

  fun getIndicesDir(): Path {
    return ourTestIndicesDir ?: MavenUtil.getPluginSystemDir("Indices")
  }

  fun getIndexForRepoSync(repo: MavenRepositoryInfo): MavenIndex {
    return runBlockingMaybeCancellable {
      getIndexForRepo(repo)
    }
  }

  fun updateIndexContentSync(repo: MavenRepositoryInfo,
                             fullUpdate: Boolean,
                             explicit: Boolean,
                             indicator: MavenProgressIndicator) {
    return runBlockingMaybeCancellable {
      updateLuceneIndexContent(repo, fullUpdate, explicit, indicator)
    }
  }

  private suspend fun updateLuceneIndexContent(repo: MavenRepositoryInfo,
                                               fullUpdate: Boolean,
                                               explicit: Boolean,
                                               indicator: MavenProgressIndicator) {

    coroutineScope {

      val updateScope = this
      val connection = ApplicationManager.getApplication().messageBus.connect(updateScope)
      connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appClosing() {
          updateScope.cancel()
          indicator.cancel()
          MavenLog.LOG.info("Application is closing, gracefully shutdown all indexing operations")
        }
      })

      startUpdateLuceneIndex(repo)
    }


  }

  fun startUpdateLuceneIndex(repo: MavenRepositoryInfo) {
    val indexFile = getDirForMavenIndex(repo).toFile()
    val status = getIndexWrapper().startIndexing(repo, indexFile)
    needPoll = true
    if (status != null) {
      luceneUpdateStatusMap[repo.url] = status;
      ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).indexStatusChanged(status)
    }
  }

  fun stopIndexing(repo: MavenRepositoryInfo) {
    getIndexWrapper().stopIndexing(repo)
  }

  fun status(): List<MavenIndexUpdateState> = getIndexWrapper().status()


  private suspend fun getIndexForRepo(repo: MavenRepositoryInfo): MavenIndex {
    return cs.async(Dispatchers.IO) {
      val dir = getDirForMavenIndex(repo)
      mutex.withLock {
        luceneIndices[dir.toString()]?.let { return@async it }

        val holder = getProperties(dir) ?: MavenIndexUtils.IndexPropertyHolder(
          dir.toFile(),
          repo.kind,
          setOf(repo.id),
          repo.url
        )
        return@async MavenIndexImpl(getIndexWrapper(), holder).also { luceneIndices[dir.toString()] = it }
      }
    }.await()
  }

  private fun getProperties(dir: Path): MavenIndexUtils.IndexPropertyHolder? {
    try {
      return MavenIndexUtils.readIndexProperty(dir.toFile())
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
    if (File(repo.url).isDirectory) return File(repo.url).canonicalPath
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

  @Deprecated("")
  fun getOrCreateIndices(project: Project): MavenIndices {
    return getIndexWrapper().getOrCreateIndices(project)
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
          existed.addAll(it.getSearchIndices())
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
        action(idx.value);
      }
    }
  }

  fun scheduleUpdateIndexContent(toUpdate: List<MavenUpdatableIndex>, explicit: Boolean) {
    val luceneUpdate = ArrayList<MavenIndex>()
    val inMemoryUpdate = ArrayList<MavenGAVIndex>()
    for (idx: MavenUpdatableIndex in toUpdate) {
      if (idx is MavenIndex && idx in luceneIndices.values) luceneUpdate.add(idx)
      else if (idx is MavenGAVIndex && idx in inMemoryIndices.values
               && gavUpdatingIndixes.putIfAbsent(idx, true) == null) inMemoryUpdate.add(idx)
    }

    inMemoryUpdate.forEach { idx ->
      cs.async(Dispatchers.IO) {
        withRawProgressReporter {
          val indicator = MavenProgressIndicator(null, null)
          try {
            (idx as MavenUpdatableIndex).updateOrRepair(true, indicator, explicit)
          }
          finally {
            gavUpdatingIndixes.remove(idx)
          }

        }
      }
    }

    luceneUpdate.forEach { idx ->
      luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(idx.repository.url, null, null,
                                                                        MavenIndexUpdateState.State.INDEXING);
      cs.launch {
        try {
          val indicator = MavenProgressIndicator(null, null)
          idx.updateOrRepair(true, indicator, explicit)
          luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(idx.repository.url, null, null,
                                                                            MavenIndexUpdateState.State.SUCCEED)
        }
        catch (e: Throwable) {
          luceneUpdateStatusMap[idx.repository.url] = MavenIndexUpdateState(idx.repository.url, null, null,
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

  companion object {

    @JvmStatic
    fun getInstance(): MavenSystemIndicesManager = ApplicationManager.getApplication().service()


    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<IndexChangeProgressListener> = Topic("indexChangeProgressListener", IndexChangeProgressListener::class.java)
  }
}

