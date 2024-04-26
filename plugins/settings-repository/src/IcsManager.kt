// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.StreamProvider
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.GitRepositoryService
import org.jetbrains.settingsRepository.git.processChildren
import java.io.InputStream
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

internal val LOG = logger<IcsManager>()

internal val icsManager by lazy(LazyThreadSafetyMode.NONE) {
  service<IcsManagerService>().icsManager
}

@OptIn(FlowPreview::class)
class IcsManager @JvmOverloads constructor(
  dir: Path,
  coroutineScope: CoroutineScope,
  val schemeManagerFactory: Lazy<SchemeManagerFactoryBase> = lazy { (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase) },
) {
  val credentialsStore = lazy { IcsCredentialsStore() }

  val settingsFile: Path = dir.resolve("config.json")

  val settings: IcsSettings = try {
    loadSettings(settingsFile)
  }
  catch (e: Exception) {
    LOG.error(e)
    IcsSettings()
  }

  val repositoryManager: GitRepositoryManager = GitRepositoryManager(credentialsStore, dir.resolve("repository"))
  val readOnlySourcesManager = ReadOnlySourceManager(this, dir)

  val repositoryService: RepositoryService = GitRepositoryService()

  private val commitRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      commitRequests
        .debounce(settings.commitDelay.milliseconds)
        .collect {
          @Suppress("DialogTitleCapitalization")
          runBackgroundableTask(icsMessage("task.commit.title")) {
            runCatching {
              runBlockingCancellable {
                repositoryManager.commit(fixStateIfCannotCommit = false)
              }
            }.getOrLogException(LOG)
          }
        }
    }

    coroutineScope.coroutineContext.job.invokeOnCompletion {
      repositoryManager.dispose()
    }
  }

  @Volatile
  private var autoCommitEnabled = true

  @Volatile
  var isRepositoryActive = false

  val isActive: Boolean
    get() = isRepositoryActive || readOnlySourcesManager.repositories.isNotEmpty()

  internal val autoSyncManager = AutoSyncManager(this)
  internal val syncManager = SyncManager(this, autoSyncManager)

  private fun scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication()!!.isUnitTestMode) {
      check(commitRequests.tryEmit(Unit))
    }
  }

  suspend fun sync(syncType: SyncType, localRepositoryInitializer: (() -> Unit)? = null): Boolean {
    return syncManager.sync(syncType, localRepositoryInitializer)
  }

  private fun cancelAndDisableAutoCommit() {
    if (autoCommitEnabled) {
      autoCommitEnabled = false
      check(commitRequests.tryEmit(Unit))
    }
  }

  suspend fun runInAutoCommitDisabledMode(task: suspend () -> Unit) {
    cancelAndDisableAutoCommit()
    try {
      task()
    }
    finally {
      autoCommitEnabled = true
      isRepositoryActive = repositoryManager.isRepositoryExists()
    }
  }

  fun runInAutoCommitDisabledModeSync(task: () -> Unit) {
    cancelAndDisableAutoCommit()
    try {
      task()
    }
    finally {
      autoCommitEnabled = true
      isRepositoryActive = repositoryManager.isRepositoryExists()
    }
  }


  fun setApplicationLevelStreamProvider() {
    val storageManager = ApplicationManager.getApplication().stateStore.storageManager
    // just to be sure
    storageManager.removeStreamProvider(IcsStreamProvider::class.java)
    storageManager.addStreamProvider(IcsStreamProvider(), first = true)
  }

  fun beforeApplicationLoaded(app: Application) {
    isRepositoryActive = repositoryManager.isRepositoryExists()

    app.stateStore.storageManager.addStreamProvider(IcsStreamProvider())

    val messageBusConnection = app.messageBus.simpleConnect()
    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        autoSyncManager.autoSync(true)
      }
    })
    messageBusConnection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        if (!ApplicationManagerEx.getApplicationEx().isExitInProgress) {
          autoSyncManager.autoSync()
        }
      }
    })
  }

  inner class IcsStreamProvider : StreamProvider {
    override val enabled: Boolean
      get() = this@IcsManager.isActive

    override val isExclusive: Boolean
      get() = isRepositoryActive

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = isRepositoryActive && roamingType.isRoamable

    override fun processChildren(path: String,
                                 roamingType: RoamingType,
                                 filter: (name: String) -> Boolean,
                                 processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
      val fullPath = toRepositoryPath(path, roamingType)

      // first, we must load read-only schemes - scheme could be overridden if bundled or read-only, so, such schemes must be loaded first
      for (repository in readOnlySourcesManager.repositories) {
        repository.processChildren(fullPath, filter) { name, input -> processor(name, input, true) }
      }

      if (!isRepositoryActive) {
        return false
      }

      repositoryManager.processChildren(fullPath, filter) { name, input -> processor(name, input, false) }
      return true
    }

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      if (doSave(fileSpec, content, roamingType)) {
        scheduleCommit()
      }
    }

    fun doSave(fileSpec: String, content: ByteArray, roamingType: RoamingType): Boolean =
      repositoryManager.write(toRepositoryPath(fileSpec, roamingType), content)

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (!isApplicable(fileSpec, roamingType)) {
        return false
      }

      repositoryManager.read(toRepositoryPath(fileSpec, roamingType), consumer)
      return true
    }

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      if (!isRepositoryActive) {
        return false
      }

      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Delete is prohibited now")
      }

      if (repositoryManager.delete(toRepositoryPath(fileSpec, roamingType))) {
        scheduleCommit()
      }

      return true
    }

    override fun deleteIfObsolete(fileSpec: String, roamingType: RoamingType) {
      if (roamingType == RoamingType.DISABLED) {
        delete(fileSpec, roamingType)
      }
    }
  }
}

@Service
private class IcsManagerService(private val coroutineScope: CoroutineScope) {
  lateinit var icsManager: IcsManager

  fun init(app: Application, configPath: Path) {
    val customPath = System.getProperty("ics.settingsRepository")
    val dir = if (customPath == null) configPath.resolve("settingsRepository") else Path.of(FileUtil.expandUserHome(customPath))
    val icsManager = IcsManager(dir = dir, coroutineScope = coroutineScope)
    this.icsManager = icsManager
    icsManager.beforeApplicationLoaded(app)
  }
}

private class IcsApplicationLoadListener : ApplicationLoadListener {
  override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
    if (application.isUnitTestMode) {
      return
    }

    application.serviceAsync<IcsManagerService>().init(application, configPath)
  }
}