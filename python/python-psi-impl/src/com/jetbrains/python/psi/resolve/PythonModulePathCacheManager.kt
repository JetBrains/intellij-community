package com.jetbrains.python.psi.resolve

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCopyEvent
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileMoveEvent
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Service(Service.Level.PROJECT)
internal class PythonModulePathCacheManager(
  private val project: Project,
) : Disposable {
  private val pathCache: ConcurrentMap<Module, PythonModulePathCache> = ConcurrentHashMap()

  init {
    val connection = project.getMessageBus().connect(this)

    connection.subscribe<ModuleRootListener>(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        if (event.isCausedByWorkspaceModelChangesOnly()) return
        val sdks = HashSet<Sdk>()
        for (module in pathCache.keys) {
          val sdk = PythonSdkUtil.findPythonSdk(module)
          if (sdk != null) {
            sdks.add(sdk)
          }
        }
        for (sdk in sdks) {
          updateCacheForSdk(project, sdk)
        }
        for (cache in pathCache.values) {
          cache.clearCache()
        }
      }
    })

    connection.subscribe(WorkspaceModelTopics.CHANGED, WorkspaceListener(project, pathCache))

    connection.subscribe(PyPackageManager.PACKAGE_MANAGER_TOPIC, PyPackageManager.Listener { sdk ->
      for (module in pathCache.keys) {
        val moduleSdk = PythonSdkUtil.findPythonSdk(module)
        if (moduleSdk != null && moduleSdk === sdk) {
          updateCacheForSdk(project, sdk)
          pathCache.computeIfPresent(module) { _, cache ->
            cache.clearCache()
            cache
          }
        }
      }
    })

    VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(pathCache), this)
  }


  private class WorkspaceListener(
    private val myProject: Project,
    private val pathCache: ConcurrentMap<Module, PythonModulePathCache>,
  ) : WorkspaceModelChangeListener {
    override fun changed(event: VersionedStorageChange) {
      val sdks = pathCache.keys.mapNotNull { PythonSdkUtil.findPythonSdk(it) }.toSet()
      for (sdk in sdks) {
        updateCacheForSdk(myProject, sdk)
      }
      val changes = event.getChanges(ModuleEntity::class.java)
      for (change in changes) {
        val entity = when (change) {
          is EntityChange.Replaced<ModuleEntity> -> change.oldEntity
          is EntityChange.Removed<ModuleEntity> -> change.oldEntity
          is EntityChange.Added<*> -> null
        }
        if (entity != null) {
          val module = entity.findModule(event.storageBefore) ?: continue
          val newValue = pathCache.computeIfPresent(module) { _, v ->
            v.clearCache()
            v
          }
          if (newValue != null) {
            return
          }
        }
      }

      // Invalidate caches if source roots are added or removed for this module
      val sourceRootChanges = event.getChanges(SourceRootEntity::class.java)
      for (change in sourceRootChanges) {
        val (sourceRootEntity, storage) = when (change) {
          is EntityChange.Added<SourceRootEntity> -> change.newEntity to event.storageAfter
          is EntityChange.Removed<SourceRootEntity> -> change.oldEntity to event.storageBefore
          is EntityChange.Replaced<*> -> null to event.storageBefore
        }
        if (sourceRootEntity != null) {
          val moduleEntity = sourceRootEntity.contentRoot.module
          val module = moduleEntity.findModule(storage) ?: continue
          pathCache.computeIfPresent(module) { _, v ->
            v.clearCache()
            v
          }
        }
      }
    }
  }

  private class MyVirtualFileListener(private val pathCache: ConcurrentMap<Module, PythonModulePathCache>) : VirtualFileListener {
    override fun fileCreated(event: VirtualFileEvent) {
      clearCache()
    }

    override fun fileDeleted(event: VirtualFileEvent) {
      clearCache()
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
      clearCache()
    }

    override fun fileCopied(event: VirtualFileCopyEvent) {
      clearCache()
    }

    override fun propertyChanged(event: VirtualFilePropertyEvent) {
      if (event.propertyName == VirtualFile.PROP_NAME) {
        clearCache()
      }
    }

    private fun clearCache() {
      for (cache in pathCache.values) {
        cache.clearCache()
      }
    }
  }

  fun getPythonPathCache(module: Module): PythonModulePathCache {
    return pathCache.computeIfAbsent(module) {
      Disposer.register(module) {
        pathCache.remove(module)
      }
      PythonModulePathCache(module)
    }
  }

  override fun dispose() = Unit

  companion object {
    @JvmStatic
    fun getInstance(project: Project): PythonModulePathCacheManager = project.service<PythonModulePathCacheManager>()


    fun updateCacheForSdk(project: Project, sdk: Sdk) {
      // initialize cache for SDK
      PythonSdkPathCache.getInstance(project, sdk)
    }
  }
}
