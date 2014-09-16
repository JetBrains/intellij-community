package org.jetbrains.plugins.settingsRepository

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StorageUtil
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.project.impl.ProjectLifecycleListener
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.SingleAlarm

import java.io.File
import java.io.InputStream
import org.jetbrains.plugins.settingsRepository.git.GitRepositoryManager
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.intellij.openapi.util.SystemInfo
import com.mcdermottroe.apple.OSXKeychain
import org.jetbrains.plugins.settingsRepository.git.GitRepositoryService

val PLUGIN_NAME: String = "Settings Repository"

val LOG: Logger = Logger.getInstance(javaClass<IcsManager>())

enum class SyncType {
  MERGE
  RESET_TO_THEIRS
  RESET_TO_MY
}

class AuthenticationException(message: String, cause: Throwable) : Exception(message, cause)

private fun getPathToBundledFile(filename: String): String {
  val url = javaClass<IcsManager>().getResource("")!!
  val folder: String
  if ("jar" == url.getProtocol()) {
    // running from build
    folder = "/plugins/settings-repository"
  }
  else {
    // running from sources
    folder = "/settings-repository"
  }
  return FileUtil.toSystemDependentName(PathManager.getHomePath() + folder + "/lib/" + filename)
}

fun getPluginSystemDir(): File {
  val customPath = System.getProperty("ics.settingsRepository")
  if (customPath == null) {
    return File(PathManager.getSystemPath(), "settingsRepository")
  }
  else {
    return File(FileUtil.expandUserHome(customPath))
  }
}

private fun updateStoragesFromStreamProvider(appStorageManager: StateStorageManager, storageFileNames: Collection<String>) {
  for (storageFileName in storageFileNames) {
    val stateStorage = appStorageManager.getFileStateStorage(storageFileName)
    if (stateStorage is FileBasedStorage) {
      try {
        stateStorage.resetProviderCache()
        stateStorage.updateFileExternallyFromStreamProviders()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }
}

public class IcsManager : ApplicationLoadListener {
  class object {
    fun getInstance() = ApplicationLoadListener.EP_NAME.findExtension(javaClass<IcsManager>())!!
  }

  public val repositoryService: RepositoryService = GitRepositoryService()

  private val settings = IcsSettings()

  val repositoryManager: RepositoryManager = GitRepositoryManager(object : AtomicNotNullLazyValue<CredentialsStore>() {
    override fun compute(): CredentialsStore {
      if (SystemInfo.isMacIntel64 && SystemInfo.isMacOSLeopard) {
        try {
          OSXKeychain.setLibraryPath(getPathToBundledFile("osxkeychain.so"))
          return OsXCredentialsStore()
        }
        catch (e: Exception) {
          LOG.error(e)
        }
      }
      return FileCredentialsStore(File(getPluginSystemDir(), ".git_auth"))
    }
  })

  private val commitAlarm = SingleAlarm(object : Runnable {
    override fun run() {
      ProgressManager.getInstance().run(object : Task.Backgroundable(null, IcsBundle.message("task.commit.title")) {
        override fun run(indicator: ProgressIndicator) {
          try {
            repositoryManager.commit(indicator)
          }
          catch (e: Exception) {
            LOG.error(e)
          }
        }
      })
    }
  }, settings.commitDelay)

  private volatile var autoCommitEnabled = true
  private volatile var writeAndDeleteProhibited: Boolean = false

  volatile var repositoryActive: Boolean = false

  private fun scheduleCommit() {
    if (autoCommitEnabled && !ApplicationManager.getApplication()!!.isUnitTestMode()) {
      commitAlarm.cancelAndRequest()
    }
  }

  private inner class ApplicationLevelProvider : IcsStreamProvider(null) {
    override fun listSubFiles(fileSpec: String, roamingType: RoamingType): MutableCollection<String> = repositoryManager.listSubFileNames(buildPath(fileSpec, roamingType, null)) as MutableCollection<String>

    override fun delete(fileSpec: String, roamingType: RoamingType) {
      if (writeAndDeleteProhibited) {
        throw IllegalStateException("Delete is prohibited now")
      }

      repositoryManager.delete(buildPath(fileSpec, roamingType, null))
      scheduleCommit()
    }
  }

  private fun registerProjectLevelProviders(project: Project) {
    val storageManager = (project as ProjectEx).getStateStore().getStateStorageManager()
    val projectId = storageManager.getStateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED)!!.getState(ProjectId(), "IcsProjectId", javaClass<ProjectId>(), null)
    if (projectId == null || projectId.uid == null) {
      // not mapped, if user wants, he can map explicitly, we don't suggest
      // we cannot suggest "map to ICS" for any project that user opens, it will be annoying
      return
    }

    storageManager.setStreamProvider(ProjectLevelProvider(projectId.uid!!))

    updateStoragesFromStreamProvider(storageManager, storageManager.getStorageFileNames())
  }

  private inner class ProjectLevelProvider(projectId: String) : IcsStreamProvider(projectId) {
    override fun isAutoCommit(fileSpec: String, roamingType: RoamingType) = !StorageUtil.isProjectOrModuleFile(fileSpec)

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
      if (StorageUtil.isProjectOrModuleFile(fileSpec)) {
        // applicable only if file was committed to Settings Server explicitly
        return repositoryManager.has(buildPath(fileSpec, roamingType, this.projectId))
      }
      return settings.shareProjectWorkspace || fileSpec != StoragePathMacros.WORKSPACE_FILE
    }
  }

  public fun getSettings(): IcsSettings {
    return settings
  }

  throws(javaClass<Exception>())
  public fun sync(syncType: SyncType, project: Project?) {
    ApplicationManager.getApplication()!!.assertIsDispatchThread()

    var exception: Exception? = null

    cancelAndDisableAutoCommit()
    try {
      ApplicationManager.getApplication()!!.saveSettings()
      writeAndDeleteProhibited = true
      ProgressManager.getInstance().run(object : Task.Modal(project, IcsBundle.message("task.sync.title"), true) {
        override fun run(indicator: ProgressIndicator) {
          indicator.setIndeterminate(true)

          try {
            // we commit before even if sync "RESET_TO_THEIRS" — preserve history and ability to undo
            repositoryManager.commit(indicator)
          }
          catch (e: Exception) {
            LOG.error(e)

            // "RESET_TO_*" will do "reset hard", so, probably, error will be gone, so, we can continue operation
            if (syncType == SyncType.MERGE) {
              exception = e
              return
            }
          }

          try {
            when (syncType) {
              SyncType.MERGE -> {
                repositoryManager.pull(indicator)
                repositoryManager.push(indicator)
              }
              // we don't push - probably, repository will be modified/removed (user can do something, like undo) before any other next push activities (so, we don't want to disturb remote)
              SyncType.RESET_TO_THEIRS -> repositoryManager.resetToTheirs(indicator)
              SyncType.RESET_TO_MY -> {
                repositoryManager.resetToMy(indicator)
                repositoryManager.push(indicator)
              }
            }
          }
          catch (e: Exception) {
            if (e !is AuthenticationException) {
              LOG.error(e)
            }
            exception = e
          }
        }
      })
    }
    finally {
      autoCommitEnabled = true
      writeAndDeleteProhibited = false
    }

    if (exception != null) {
      throw exception!!
    }
  }

  private fun cancelAndDisableAutoCommit() {
    if (autoCommitEnabled) {
      autoCommitEnabled = false
      commitAlarm.cancel()
    }
  }

  public fun runInAutoCommitDisabledMode(task: ()->Unit) {
    cancelAndDisableAutoCommit()
    try {
      task()
    }
    finally {
      autoCommitEnabled = true
      repositoryActive = repositoryManager.hasUpstream()
    }
  }

  override fun beforeApplicationLoaded(application: Application) {
    try {
      settings.load()
    }
    catch (e: Exception) {
      LOG.error(e)
    }

    repositoryActive = repositoryManager.hasUpstream()

    (application as ApplicationImpl).getStateStore().getStateStorageManager().setStreamProvider(ApplicationLevelProvider())

    application.getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener.Adapter() {
      override fun beforeProjectLoaded(project: Project) {
        if (!project.isDefault()) {
          registerProjectLevelProviders(project)
        }
      }
    })
  }

  private open inner class IcsStreamProvider(protected val projectId: String?) : StreamProvider() {
    override fun saveContent(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType, async: Boolean) {
      if (writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      repositoryManager.write(buildPath(fileSpec, roamingType, projectId), content, size, async)
      if (isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit()
      }
    }

    protected open fun isAutoCommit(fileSpec: String, roamingType: RoamingType): Boolean = true

    override fun loadContent(fileSpec: String, roamingType: RoamingType): InputStream? {
      return repositoryManager.read(buildPath(fileSpec, roamingType, projectId))
    }

    override fun delete(fileSpec: String, roamingType: RoamingType) {
    }

    override fun isEnabled() = repositoryActive
  }
}
