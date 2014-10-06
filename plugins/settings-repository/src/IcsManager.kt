package org.jetbrains.settingsRepository

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StoragePathMacros
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
import org.jetbrains.settingsRepository.git.GitRepositoryManager
import com.intellij.openapi.util.AtomicNotNullLazyValue
import com.mcdermottroe.apple.OSXKeychain
import org.jetbrains.settingsRepository.git.GitRepositoryService
import com.intellij.openapi.progress.ProcessCanceledException
import java.util.LinkedHashSet
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.FileBasedStorage
import javax.swing.SwingUtilities
import com.intellij.util.ui.UIUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.components.impl.stores.ComponentStoreImpl

val PLUGIN_NAME: String = "Settings Repository"

val LOG: Logger = Logger.getInstance(javaClass<IcsManager>())

enum class SyncType {
  MERGE
  RESET_TO_THEIRS
  RESET_TO_MY
}

class AuthenticationException(message: String, cause: Throwable) : Exception(message, cause)

private fun getPathToBundledFile(filename: String): String {
  var pluginsDirectory: String
  var folder = "/settings-repository"
  if ("jar" == javaClass<IcsManager>().getResource("")!!.getProtocol()) {
    // running from build
    pluginsDirectory = PathManager.getPluginsPath()
    if (!File(pluginsDirectory + folder).exists()) {
      pluginsDirectory = PathManager.getHomePath()
      folder = "/plugins" + folder
    }
  }
  else {
    // running from sources
    pluginsDirectory = PathManager.getHomePath()
  }
  return FileUtil.toSystemDependentName(pluginsDirectory + folder + "/lib/" + filename)
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

public class IcsManager : ApplicationLoadListener {
  class object {
    fun getInstance() = ApplicationLoadListener.EP_NAME.findExtension(javaClass<IcsManager>())!!
  }

  public val repositoryService: RepositoryService = GitRepositoryService()

  private val settings = IcsSettings()

  val repositoryManager: RepositoryManager = GitRepositoryManager(object : AtomicNotNullLazyValue<CredentialsStore>() {
    override fun compute(): CredentialsStore {
      if (isOSXCredentialsStoreSupported) {
        try {
          OSXKeychain.setLibraryPath(getPathToBundledFile("osxkeychain.so"))
          return OsXCredentialsStore()
        }
        catch (e: Throwable) {
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
          catch (e: Throwable) {
            LOG.error(e)
          }
        }
      })
    }
  }, settings.commitDelay)

  private volatile var autoCommitEnabled = true
  private volatile var writeAndDeleteProhibited = false

  volatile var repositoryActive = false

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
    // updateStoragesFromStreamProvider(storageManager, storageManager.getStorageFileNames())
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

  public fun sync(syncType: SyncType, project: Project?, localRepositoryInitializer: (() -> Unit)? = null) {
    ApplicationManager.getApplication()!!.assertIsDispatchThread()

    var exception: Throwable? = null
    var restartApplication = false
    cancelAndDisableAutoCommit()
    try {
      ApplicationManager.getApplication()!!.saveSettings()
      writeAndDeleteProhibited = true
      ProgressManager.getInstance().run(object : Task.Modal(project, IcsBundle.message("task.sync.title"), true) {
        override fun run(indicator: ProgressIndicator) {
          indicator.setIndeterminate(true)

          if (localRepositoryInitializer == null) {
            try {
              // we commit before even if sync "RESET_TO_THEIRS" — preserve history and ability to undo
              if (repositoryManager.canCommit()) {
                repositoryManager.commit(indicator)
              }
              // well, we cannot commit? No problem, upcoming action must do something smart and solve the situation
            }
            catch (e: Throwable) {
              LOG.error(e)

              // "RESET_TO_*" will do "reset hard", so, probably, error will be gone, so, we can continue operation
              if (syncType == SyncType.MERGE) {
                exception = e
                return
              }
            }
          }

          if (indicator.isCanceled()) {
            return
          }

          var updateResult: UpdateResult? = null
          try {
            when (syncType) {
              SyncType.MERGE -> {
                updateResult = repositoryManager.pull(indicator)
                if (localRepositoryInitializer != null) {
                  // must be performed only after initial pull, so, local changes will be relative to remote files
                  localRepositoryInitializer()
                  repositoryManager.commit(indicator)
                  updateResult = updateResult.concat(repositoryManager.pull(indicator))
                }
                repositoryManager.push(indicator)
              }
              SyncType.RESET_TO_THEIRS -> {
                // we don't push - probably, repository will be modified/removed (user can do something, like undo) before any other next push activities (so, we don't want to disturb remote)
                updateResult = repositoryManager.resetToTheirs(indicator)
              }
              SyncType.RESET_TO_MY -> {
                updateResult = repositoryManager.resetToMy(indicator, localRepositoryInitializer)
                repositoryManager.push(indicator)
              }
            }
          }
          catch (e: ProcessCanceledException) {
          }
          catch (e: Throwable) {
            if (e !is AuthenticationException) {
              LOG.error(e)
            }
            exception = e
          }

          if (updateResult != null) {
            restartApplication = updateStoragesFromStreamProvider((ApplicationManager.getApplication() as ApplicationImpl).getStateStore(), updateResult!!)
          }
        }
      })
    }
    finally {
      autoCommitEnabled = true
      writeAndDeleteProhibited = false
    }

    if (restartApplication) {
      // force to avoid saveAll & confirmation
      (ApplicationManager.getApplication() as ApplicationImpl).exit(true, true, true, true)
      return
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

  override fun beforeApplicationLoaded(application: Application) {
    try {
      settings.load()
    }
    catch (e: Exception) {
      LOG.error(e)
    }

    repositoryActive = repositoryManager.isRepositoryExists()

    (application as ApplicationImpl).getStateStore().getStateStorageManager().setStreamProvider(ApplicationLevelProvider())

    application.getMessageBus().connect().subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener.Adapter() {
      override fun beforeProjectLoaded(project: Project) {
        if (!project.isDefault()) {
          registerProjectLevelProviders(project)
        }
      }
    })
  }

  open inner class IcsStreamProvider(protected val projectId: String?) : StreamProvider() {
    override fun saveContent(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType, async: Boolean) {
      if (writeAndDeleteProhibited) {
        throw IllegalStateException("Save is prohibited now")
      }

      doSave(fileSpec, content, size, roamingType)

      if (isAutoCommit(fileSpec, roamingType)) {
        scheduleCommit()
      }
    }

    fun doSave(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      repositoryManager.write(buildPath(fileSpec, roamingType, projectId), content, size)
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

fun invokeAndWaitIfNeed(runnable: ()->Unit) {
  if (SwingUtilities.isEventDispatchThread()) runnable() else SwingUtilities.invokeAndWait(runnable)
}

private fun updateStoragesFromStreamProvider(store: IComponentStore.Reloadable, updateResult: UpdateResult): Boolean {
  val changedComponentNames = LinkedHashSet<String>()
  val stateStorages = store.getStateStorageManager().getCachedFileStateStorages(updateResult.changed, updateResult.deleted)
  val changed = stateStorages.first!!
  val deleted = stateStorages.second!!
  if (changed.isEmpty() && deleted.isEmpty()) {
    return false
  }

  return UIUtil.invokeAndWaitIfNeeded(object : Computable<Boolean> {
    override fun compute(): Boolean {
      val notReloadableComponents: Collection<String>
      val token = WriteAction.start()
      try {
        updateStateStorage(changedComponentNames, changed, false)
        updateStateStorage(changedComponentNames, deleted, true)

        if (changedComponentNames.isEmpty()) {
          return false
        }

        notReloadableComponents = store.getNotReloadableComponents(changedComponentNames)
        store.reinitComponents(changedComponentNames, notReloadableComponents, false)
      }
      finally {
        token.finish()
      }

      if (notReloadableComponents.isEmpty()) {
        return false
      }
      return ComponentStoreImpl.askToRestart(store, notReloadableComponents, null)
    }
  })!!
}

private fun updateStateStorage(changedComponentNames: Set<String>, stateStorages: Collection<FileBasedStorage>, deleted: Boolean) {
  for (stateStorage in stateStorages) {
    try {
      stateStorage.updatedFromStreamProvider(changedComponentNames, deleted)
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }
}