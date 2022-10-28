// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.StreamProvider
import com.intellij.configurationStore.schemeManager.SchemeManagerFactoryBase
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.GitRepositoryService
import org.jetbrains.settingsRepository.git.processChildren
import java.io.InputStream
import java.nio.file.Path

internal val LOG = logger<IcsManager>()

internal val icsManager by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationLoadListener.EP_NAME.findExtensionOrFail(IcsApplicationLoadListener::class.java).icsManager!!
}

class IcsManager @JvmOverloads constructor(dir: Path,
                                           parentDisposable: Disposable,
                                           val schemeManagerFactory: Lazy<SchemeManagerFactoryBase> = lazy { (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase) }) : Disposable {
  val credentialsStore = lazy { IcsCredentialsStore() }

  val settingsFile: Path = dir.resolve("config.json")

  val settings: IcsSettings
  val repositoryManager: RepositoryManager = GitRepositoryManager(credentialsStore, dir.resolve("repository"), this)
  val readOnlySourcesManager = ReadOnlySourceManager(this, dir)

  init {
    Disposer.register(parentDisposable, this)

    settings = try {
      loadSettings(settingsFile)
    }
    catch (e: Exception) {
      LOG.error(e)
      IcsSettings()
    }
  }

  override fun dispose() {
  }

  val repositoryService: RepositoryService = GitRepositoryService()

  private val commitAlarm = SingleAlarm(Runnable {
    runBackgroundableTask(icsMessage("task.commit.title")) {
      LOG.runAndLogException {
        runBlockingCancellable {
          repositoryManager.commit(fixStateIfCannotCommit = false)
        }
      }
    }
  }, settings.commitDelay)

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
      commitAlarm.cancelAndRequest()
    }
  }

  suspend fun sync(syncType: SyncType, project: Project? = null, localRepositoryInitializer: (() -> Unit)? = null): Boolean {
    return syncManager.sync(syncType, project, localRepositoryInitializer)
  }

  private fun cancelAndDisableAutoCommit() {
    if (autoCommitEnabled) {
      autoCommitEnabled = false
      commitAlarm.cancel()
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

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = isRepositoryActive

    override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
      val fullPath = toRepositoryPath(path, roamingType)

      // first we must load read-only schemes - scheme could be overridden if bundled or read-only, so, such schemes must be loaded first
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
      if (!isRepositoryActive) {
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
  }
}

internal class IcsApplicationLoadListener : ApplicationLoadListener {
  var icsManager: IcsManager? = null
    private set

  override fun beforeApplicationLoaded(application: Application, configPath: Path) {
    if (application.isUnitTestMode) {
      return
    }

    val customPath = System.getProperty("ics.settingsRepository")
    val pluginSystemDir = if (customPath == null) configPath.resolve("settingsRepository") else Path.of(FileUtil.expandUserHome(customPath))
    @Suppress("IncorrectParentDisposable") // this plugin is special and can't be dynamic anyway
    val icsManager = IcsManager(pluginSystemDir, application)
    this.icsManager = icsManager

    val repositoryManager = icsManager.repositoryManager
    if (repositoryManager.isRepositoryExists() && repositoryManager is GitRepositoryManager) {
      val osFolderName = getOsFolderName()

      val migrateSchemes = repositoryManager.renameDirectory(linkedMapOf(
          Pair("\$ROOT_CONFIG$", null),
          Pair("$osFolderName/\$ROOT_CONFIG$", osFolderName),

          Pair("\$APP_CONFIG$", null),
          Pair("$osFolderName/\$APP_CONFIG$", osFolderName)
      ), "Get rid of \$ROOT_CONFIG$ and \$APP_CONFIG")

      val migrateKeyMaps = repositoryManager.renameDirectory(linkedMapOf(
          Pair("$osFolderName/keymaps", "keymaps")
      ), "Move keymaps to root")

      val removeOtherXml = repositoryManager.delete("other.xml")
      if (migrateSchemes || migrateKeyMaps || removeOtherXml) {
        // schedule push to avoid merge conflicts
        application.invokeLater {
          icsManager.autoSyncManager.autoSync(force = true)
        }
      }
    }

    icsManager.beforeApplicationLoaded(application)
  }
}