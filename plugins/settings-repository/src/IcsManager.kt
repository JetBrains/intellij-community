/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.settingsRepository

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.StreamProvider
import com.intellij.configurationStore.isProjectOrModuleFile
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.keychain.FileCredentialsStore
import org.jetbrains.keychain.OsXCredentialsStore
import org.jetbrains.keychain.isOSXCredentialsStoreSupported
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.GitRepositoryService
import org.jetbrains.settingsRepository.git.processChildren
import java.io.File
import java.io.InputStream
import kotlin.properties.Delegates

val PLUGIN_NAME: String = "Settings Repository"

val LOG: Logger = Logger.getInstance(javaClass<IcsManager>())

val icsManager by Delegates.lazy {
  ApplicationLoadListener.EP_NAME.findExtension(javaClass<IcsApplicationLoadListener>()).icsManager
}

class IcsManager(dir: File) {
  val credentialsStore = object : AtomicNotNullLazyValue<CredentialsStore>() {
    override fun compute(): CredentialsStore {
      if (isOSXCredentialsStoreSupported && SystemProperties.getBooleanProperty("ics.use.osx.keychain", true)) {
        try {
          return OsXCredentialsStore("IntelliJ Platform Settings Repository")
        }
        catch (e: Throwable) {
          LOG.error(e)
        }
      }
      return FileCredentialsStore(File(dir, ".git_auth"))
    }
  }

   val settingsFile = File(dir, "config.json")

  val settings: IcsSettings
  val repositoryManager: RepositoryManager = GitRepositoryManager(credentialsStore, File(dir, "repository"))

  init {
    try {
      settings = loadSettings(settingsFile)
    }
    catch (e: Exception) {
      settings = IcsSettings()
      LOG.error(e)
    }
  }

  val readOnlySourcesManager = ReadOnlySourcesManager(settings, dir)

  val repositoryService: RepositoryService = GitRepositoryService()

  private val commitAlarm = SingleAlarm(object : Runnable {
    override fun run() {
      ProgressManager.getInstance().run(object : Task.Backgroundable(null, IcsBundle.message("task.commit.title")) {
        override fun run(indicator: ProgressIndicator) {
          try {
            repositoryManager.commit(indicator)
          }
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      })
    }
  }, settings.commitDelay)

  private volatile var autoCommitEnabled = true

  volatile var repositoryActive = false

  val autoSyncManager = AutoSyncManager(this)
  private val syncManager = SyncManager(this, autoSyncManager)

  private fun scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication()!!.isUnitTestMode()) {
      commitAlarm.cancelAndRequest()
    }
  }

  inner class ApplicationLevelProvider : IcsStreamProvider(null) {
    override fun delete(fileSpec: String, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Delete is prohibited now")
      }

      repositoryManager.delete(buildPath(fileSpec, roamingType))
      scheduleCommit()
    }
  }

  private fun registerProjectLevelProviders(project: Project) {
    val storageManager = project.stateStore.getStateStorageManager()
    val projectId = storageManager.getStateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED).getState(ProjectId(), "IcsProjectId", javaClass<ProjectId>())
    if (projectId == null || projectId.uid == null) {
      // not mapped, if user wants, he can map explicitly, we don't suggest
      // we cannot suggest "map to ICS" for any project that user opens, it will be annoying
      return
    }

//    storageManager.setStreamProvider(ProjectLevelProvider(projectId.uid!!))
    // updateStoragesFromStreamProvider(storageManager, storageManager.getStorageFileNames())
  }

  private inner class ProjectLevelProvider(projectId: String) : IcsStreamProvider(projectId) {
    override fun isAutoCommit(fileSpec: String, roamingType: RoamingType) = !isProjectOrModuleFile(fileSpec)

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
      if (isProjectOrModuleFile(fileSpec)) {
        // applicable only if file was committed to Settings Server explicitly
        return repositoryManager.has(buildPath(fileSpec, roamingType, this.projectId))
      }
      return settings.shareProjectWorkspace || fileSpec != StoragePathMacros.WORKSPACE_FILE
    }
  }

  fun sync(syncType: SyncType, project: Project? = null, localRepositoryInitializer: (() -> Unit)? = null) = syncManager.sync(syncType, project, localRepositoryInitializer)

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

  fun beforeApplicationLoaded(application: Application) {
    repositoryActive = repositoryManager.isRepositoryExists()

    (application.stateStore.getStateStorageManager() as StateStorageManagerImpl).streamProvider = ApplicationLevelProvider()

    autoSyncManager.registerListeners(application)

    application.getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener.Adapter() {
      override fun beforeProjectLoaded(project: Project) {
        if (project.isDefault()) {
          return
        }

        //registerProjectLevelProviders(project)
        autoSyncManager.registerListeners(project)
      }

      override fun afterProjectClosed(project: Project) {
        autoSyncManager.autoSync()
      }
    })
  }

  open inner class IcsStreamProvider(protected val projectId: String?) : StreamProvider {
    override val enabled: Boolean
      get() = repositoryActive

    override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) {
      val fullPath = buildPath(path, roamingType, null)

      // first of all we must load read-only schemes - scheme could be overridden if bundled or read-only, so, such schemes must be loaded first
      for (repository in readOnlySourcesManager.repositories) {
        repository.processChildren(fullPath, filter, { name, input -> processor(name, input, true) })
      }

      repositoryManager.processChildren(fullPath, filter, { name, input -> processor(name, input, false) })
    }

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      if (doSave(fileSpec, content, size, roamingType) && isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit()
      }
    }

    fun doSave(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) = repositoryManager.write(buildPath(fileSpec, roamingType, projectId), content, size)

    protected open fun isAutoCommit(fileSpec: String, roamingType: RoamingType): Boolean = true

    override fun read(fileSpec: String, roamingType: RoamingType): InputStream? {
      return repositoryManager.read(buildPath(fileSpec, roamingType, projectId))
    }

    override fun delete(fileSpec: String, roamingType: RoamingType) {
    }
  }
}

class IcsApplicationLoadListener : ApplicationLoadListener {
  var icsManager: IcsManager by Delegates.notNull()
    private set

  override fun beforeApplicationLoaded(application: Application, configPath: String) {
    if (application.isUnitTestMode()) {
      return
    }

    val customPath = System.getProperty("ics.settingsRepository")
    val pluginSystemDir = if (customPath == null) File(configPath, "settingsRepository") else File(FileUtil.expandUserHome(customPath))
    icsManager = IcsManager(pluginSystemDir)

    if (!pluginSystemDir.exists()) {
      try {
        val oldPluginDir = File(PathManager.getSystemPath(), "settingsRepository")
        if (oldPluginDir.exists()) {
          FileUtil.rename(oldPluginDir, pluginSystemDir)
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    val repositoryManager = icsManager.repositoryManager
    if (repositoryManager.isRepositoryExists() && repositoryManager is GitRepositoryManager) {
      if (repositoryManager.renameDirectory(linkedMapOf(
        Pair("\$ROOT_CONFIG$", null),
        Pair("_mac/\$ROOT_CONFIG$", "_mac"),
        Pair("_windows/\$ROOT_CONFIG$", "_windows"),
        Pair("_linux/\$ROOT_CONFIG$", "_linux"),
        Pair("_freebsd/\$ROOT_CONFIG$", "_freebsd"),
        Pair("_unix/\$ROOT_CONFIG$", "_unix"),
        Pair("_unknown/\$ROOT_CONFIG$", "_unknown"),

        Pair("\$APP_CONFIG$", null),
        Pair("_mac/\$APP_CONFIG$", "_mac"),
        Pair("_windows/\$APP_CONFIG$", "_windows"),
        Pair("_linux/\$APP_CONFIG$", "_linux"),
        Pair("_freebsd/\$APP_CONFIG$", "_freebsd"),
        Pair("_unix/\$APP_CONFIG$", "_unix"),
        Pair("_unknown/\$APP_CONFIG$", "_unknown")
      ))) {
        // schedule push to avoid merge conflicts
        application.invokeLater(Runnable { icsManager.autoSyncManager.autoSync(force = true) })
      }
    }

    icsManager.beforeApplicationLoaded(application)
  }
}