/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.credentialStore.macOs.isMacOsCredentialStoreSupported
import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.SystemProperties
import com.intellij.util.exists
import com.intellij.util.move
import org.jetbrains.keychain.CredentialsStore
import org.jetbrains.keychain.FileCredentialsStore
import org.jetbrains.keychain.OsXCredentialsStore
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import org.jetbrains.settingsRepository.git.GitRepositoryService
import org.jetbrains.settingsRepository.git.processChildren
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

internal const val PLUGIN_NAME = "Settings Repository"

internal val LOG: Logger = Logger.getInstance(IcsManager::class.java)

val icsManager by lazy(LazyThreadSafetyMode.NONE) {
  ApplicationLoadListener.EP_NAME.findExtension(IcsApplicationLoadListener::class.java).icsManager
}

class IcsManager(dir: Path) {
  val credentialsStore = object : AtomicNotNullLazyValue<CredentialsStore>() {
    override fun compute(): CredentialsStore {
      if (isMacOsCredentialStoreSupported && SystemProperties.getBooleanProperty("use.osx.keychain", true)) {
        catchAndLog {
          return OsXCredentialsStore("IntelliJ Platform Settings Repository")
        }
      }
      return FileCredentialsStore(dir.resolve(".git_auth"))
    }
  }

  val settingsFile: Path = dir.resolve("config.json")

  val settings: IcsSettings
  val repositoryManager: RepositoryManager = GitRepositoryManager(credentialsStore, dir.resolve("repository"))

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

  private val commitAlarm = SingleAlarm(Runnable {
    runBackgroundableTask(icsMessage("task.commit.title")) { indicator ->
      try {
        repositoryManager.commit(indicator, fixStateIfCannotCommit = false)
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }, settings.commitDelay)

  private @Volatile var autoCommitEnabled = true

  @Volatile var repositoryActive = false

  internal val autoSyncManager = AutoSyncManager(this)
  private val syncManager = SyncManager(this, autoSyncManager)

  private fun scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication()!!.isUnitTestMode) {
      commitAlarm.cancelAndRequest()
    }
  }

  inner class ApplicationLevelProvider : IcsStreamProvider(null) {
    override fun delete(fileSpec: String, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Delete is prohibited now")
      }

      if (repositoryManager.delete(toRepositoryPath(fileSpec, roamingType))) {
        scheduleCommit()
      }
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

  fun newStreamProvider() {
    val application = ApplicationManager.getApplication()
    (application.stateStore.stateStorageManager as StateStorageManagerImpl).streamProvider = ApplicationLevelProvider()
  }

  fun beforeApplicationLoaded(application: Application) {
    repositoryActive = repositoryManager.isRepositoryExists()

    val storage = application.stateStore.stateStorageManager as StateStorageManagerImpl
    if (storage.streamProvider == null || !storage.streamProvider!!.enabled) {
      storage.streamProvider = ApplicationLevelProvider()
    }

    autoSyncManager.registerListeners(application)

    application.messageBus.connect().subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      override fun beforeProjectLoaded(project: Project) {
        if (project.isDefault) {
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

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = enabled

    override fun processChildren(path: String, roamingType: RoamingType, filter: (name: String) -> Boolean, processor: (name: String, input: InputStream, readOnly: Boolean) -> Boolean) {
      val fullPath = toRepositoryPath(path, roamingType, null)

      // first of all we must load read-only schemes - scheme could be overridden if bundled or read-only, so, such schemes must be loaded first
      for (repository in readOnlySourcesManager.repositories) {
        repository.processChildren(fullPath, filter) { name, input -> processor(name, input, true) }
      }

      repositoryManager.processChildren(fullPath, filter) { name, input -> processor(name, input, false) }
    }

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      if (syncManager.writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      if (doSave(fileSpec, content, size, roamingType) && isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit()
      }
    }

    fun doSave(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) = repositoryManager.write(toRepositoryPath(fileSpec, roamingType, projectId), content, size)

    protected open fun isAutoCommit(fileSpec: String, roamingType: RoamingType) = true

    override fun read(fileSpec: String, roamingType: RoamingType) = repositoryManager.read(toRepositoryPath(fileSpec, roamingType, projectId))

    override fun delete(fileSpec: String, roamingType: RoamingType) {
    }
  }
}

class IcsApplicationLoadListener : ApplicationLoadListener {
  var icsManager: IcsManager by Delegates.notNull()
    private set

  override fun beforeApplicationLoaded(application: Application, configPath: String) {
    val customPath = System.getProperty("ics.settingsRepository")
    val pluginSystemDir = if (customPath == null) Paths.get(configPath, "settingsRepository") else Paths.get(FileUtil.expandUserHome(customPath))
    icsManager = IcsManager(pluginSystemDir)

    if (!pluginSystemDir.exists()) {
      try {
        val oldPluginDir = Paths.get(PathManager.getSystemPath(), "settingsRepository")
        if (oldPluginDir.exists()) {
          oldPluginDir.move(pluginSystemDir)
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }

    val repositoryManager = icsManager.repositoryManager
    if (repositoryManager.isRepositoryExists() && repositoryManager is GitRepositoryManager) {
      val migrateSchemes = repositoryManager.renameDirectory(linkedMapOf(
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
      ))

      val removeOtherXml = repositoryManager.delete("other.xml")
      if (migrateSchemes || removeOtherXml) {
        // schedule push to avoid merge conflicts
        application.invokeLater({ icsManager.autoSyncManager.autoSync(force = true) })
      }
    }

    icsManager.beforeApplicationLoaded(application)
  }
}