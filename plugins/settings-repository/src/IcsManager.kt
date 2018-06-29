// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.SchemeManagerFactoryBase
import com.intellij.configurationStore.StreamProvider
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.options.SchemeManagerFactory
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.io.exists
import com.intellij.util.io.move
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.GitRepositoryService
import org.jetbrains.settingsRepository.git.processChildren
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

internal const val PLUGIN_NAME = "Settings Repository"

internal val LOG = logger<IcsManager>()

internal val icsManager by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationLoadListener.EP_NAME.findExtension(IcsApplicationLoadListener::class.java).icsManager
}

class IcsManager @JvmOverloads constructor(dir: Path, val schemeManagerFactory: Lazy<SchemeManagerFactoryBase> = lazy { (SchemeManagerFactory.getInstance() as SchemeManagerFactoryBase) }) {
  val credentialsStore: Lazy<IcsCredentialsStore> = lazy { IcsCredentialsStore() }

  val settingsFile: Path = dir.resolve("config.json")

  val settings: IcsSettings
  val repositoryManager: RepositoryManager = GitRepositoryManager(credentialsStore, dir.resolve("repository"))
  val readOnlySourcesManager: ReadOnlySourceManager = ReadOnlySourceManager(this, dir)

  init {
    settings = try {
      loadSettings(settingsFile)
    }
    catch (e: Exception) {
      LOG.error(e)
      IcsSettings()
    }
  }

  val repositoryService: RepositoryService = GitRepositoryService()

  private val commitAlarm = SingleAlarm(Runnable {
    runBackgroundableTask(icsMessage("task.commit.title")) { indicator ->
      LOG.runAndLogException {
        repositoryManager.commit(indicator, fixStateIfCannotCommit = false)
      }
    }
  }, settings.commitDelay)

  private @Volatile var autoCommitEnabled = true

  @Volatile var repositoryActive: Boolean = false

  val active: Boolean
    get() = repositoryActive || readOnlySourcesManager.repositories.isNotEmpty()

  internal val autoSyncManager = AutoSyncManager(this)
  internal val syncManager = SyncManager(this, autoSyncManager)

  private fun scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication()!!.isUnitTestMode) {
      commitAlarm.cancelAndRequest()
    }
  }

  inner class ApplicationLevelProvider : IcsStreamProvider(null) {
    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      if (!repositoryActive) {
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

//  private inner class ProjectLevelProvider(projectId: String) : IcsStreamProvider(projectId) {
//    override fun isAutoCommit(fileSpec: String, roamingType: RoamingType) = !isProjectOrModuleFile(fileSpec)
//
//    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
//      if (isProjectOrModuleFile(fileSpec)) {
//        // applicable only if file was committed to Settings Server explicitly
//        return repositoryManager.has(buildPath(fileSpec, roamingType, this.projectId))
//      }
//      return settings.shareProjectWorkspace || fileSpec != StoragePathMacros.WORKSPACE_FILE
//    }
//  }

  fun sync(syncType: SyncType, project: Project? = null, localRepositoryInitializer: (() -> Unit)? = null): Boolean = syncManager.sync(syncType, project, localRepositoryInitializer)

  private fun cancelAndDisableAutoCommit() {
    if (autoCommitEnabled) {
      autoCommitEnabled = false
      commitAlarm.cancel()
    }
  }

  fun runInAutoCommitDisabledMode(task: ()->Unit) {
    cancelAndDisableAutoCommit()
    try {
      task()
    }
    finally {
      autoCommitEnabled = true
      repositoryActive = repositoryManager.isRepositoryExists()
    }
  }

  fun setApplicationLevelStreamProvider() {
    val storageManager = ApplicationManager.getApplication().stateStore.storageManager
    // just to be sure
    storageManager.removeStreamProvider(ApplicationLevelProvider::class.java)
    storageManager.addStreamProvider(ApplicationLevelProvider(), first = true)
  }

  fun beforeApplicationLoaded(application: Application) {
    repositoryActive = repositoryManager.isRepositoryExists()

    application.stateStore.storageManager.addStreamProvider(ApplicationLevelProvider())

    val messageBusConnection = application.messageBus.connect()
    messageBusConnection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appWillBeClosed(isRestart: Boolean) {
        autoSyncManager.autoSync(true)
      }
    })
    messageBusConnection.subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      override fun beforeProjectLoaded(project: Project) {
        if (project.isDefault) {
          return
        }

        autoSyncManager.registerListeners(project)
      }

      override fun afterProjectClosed(project: Project) {
        autoSyncManager.autoSync()
      }
    })
  }

  open inner class IcsStreamProvider(private val projectId: String?) : StreamProvider {
    override val enabled: Boolean
      get() = this@IcsManager.active

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = repositoryActive

    override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean): Boolean {
      val fullPath = toRepositoryPath(path, roamingType, null)

      // first of all we must load read-only schemes - scheme could be overridden if bundled or read-only, so, such schemes must be loaded first
      for (repository in readOnlySourcesManager.repositories) {
        repository.processChildren(fullPath, filter) { name, input -> processor(name, input, true) }
      }

      if (!repositoryActive) {
        return false
      }

      repositoryManager.processChildren(fullPath, filter) { name, input -> processor(name, input, false) }
      return true
    }

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      if (doSave(fileSpec, content, size, roamingType) && isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit()
      }
    }

    fun doSave(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType): Boolean = repositoryManager.write(toRepositoryPath(fileSpec, roamingType, projectId), content, size)

    protected open fun isAutoCommit(fileSpec: String, roamingType: RoamingType): Boolean = true

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (!repositoryActive) {
        return false
      }

      repositoryManager.read(toRepositoryPath(fileSpec, roamingType, projectId), consumer)
      return true
    }

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      return false
    }
  }
}

class IcsApplicationLoadListener : ApplicationLoadListener {
  var icsManager: IcsManager by Delegates.notNull()
    private set

  override fun beforeApplicationLoaded(application: Application, configPath: String) {
    if (application.isUnitTestMode) {
      return
    }

    val customPath = System.getProperty("ics.settingsRepository")
    val pluginSystemDir = if (customPath == null) Paths.get(configPath, "settingsRepository") else Paths.get(FileUtil.expandUserHome(customPath))
    icsManager = IcsManager(pluginSystemDir)

    if (!pluginSystemDir.exists()) {
      LOG.runAndLogException {
        val oldPluginDir = appSystemDir.resolve("settingsRepository")
        if (oldPluginDir.exists()) {
          oldPluginDir.move(pluginSystemDir)
        }
      }
    }

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
        application.invokeLater({ icsManager.autoSyncManager.autoSync(force = true) })
      }
    }

    icsManager.beforeApplicationLoaded(application)
  }
}