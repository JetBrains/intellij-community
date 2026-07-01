package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.LowMemoryWatcher.LowMemoryWatcherType
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.platform.lsp.api.LspClient
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.python.lsp.core.type.LspTypeEvalContext
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import com.intellij.util.containers.ContainerUtil
import com.jetbrains.python.psi.types.PyLibraryModificationTracker
import java.util.concurrent.ConcurrentMap

/**
 * Pyrefly contains own cache for resolved types,
 * and we need to rely on it not on our psi tracker
 * as we do for the built-in resolver
 */
@Service(Service.Level.PROJECT)
internal class PyreflyLspTypeEngineFileCache(val project: Project) : Disposable.Default {
  private val lowMemoryModificationTracker = SimpleModificationTracker()


  private val cachedMapStorage = CachedValuesManager.getManager(project).createCachedValue {
    val map = ContainerUtil.createConcurrentSoftValueMap<PsiFile, LspTypeEvalContext>()
    CachedValueProvider.Result.create(map,
                                      PsiModificationTracker.getInstance(project ),
                                      lowMemoryModificationTracker,
                                      PyLspServerModificationTracker.getInstance(project))
  }

  private val librariesCachedMapStorage: CachedValue<ConcurrentMap<PsiFile, LspTypeEvalContext>> =
    CachedValuesManager.getManager(project).createCachedValue {
      val map = ContainerUtil.createConcurrentSoftValueMap<PsiFile, LspTypeEvalContext>()
      CachedValueProvider.Result.create(map,
                                        PyLibraryModificationTracker.getInstance(project),
                                        lowMemoryModificationTracker,
                                        PyLspServerModificationTracker.getInstance(project))
    }


  init {
    LowMemoryWatcher.register(
      {
        lowMemoryModificationTracker.incModificationCount()
        cachedMapStorage.value
        librariesCachedMapStorage.value
      }, LowMemoryWatcherType.ALWAYS, this)
  }


  fun getContext(file: PsiFile, lspClient: LspClient, isLibrary: Boolean): LspTypeEvalContext {
    val storage = if (isLibrary) librariesCachedMapStorage else cachedMapStorage
    val cache = storage.value
    return cache.getOrPut(file) { PyreflyTypeEvalContext(lspClient, file) }
  }

  companion object {
    fun getInstance(project: Project): PyreflyLspTypeEngineFileCache = project.service()
  }
}