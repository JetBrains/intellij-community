package com.intellij.python.pyrefly.typeEngine

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspClient
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.python.lsp.core.utils.PyLspServerModificationTracker
import com.jetbrains.python.psi.types.PyLibraryModificationTracker
import java.util.concurrent.ConcurrentHashMap

/**
 * Pyrefly contains own cache for resolved types,
 * and we need to rely on it not on our psi tracker
 * as we do for the built-in resolver
 */
@Service(Service.Level.PROJECT)
internal class PyreflyTypeEvalContextCache(val project: Project) {
  private val cachedMapStorage = CachedValuesManager.getManager(project).createCachedValue {
    CachedValueProvider.Result.create(ConcurrentHashMap<LspClient, PyreflyTypeEvalContext>(),
                                      PsiModificationTracker.getInstance(project ),
                                      PyLibraryModificationTracker.getInstance(project),
                                      PyLspServerModificationTracker.getInstance(project))
  }

  /**
   * The client is the key because a project can run more than one Pyrefly server. One server holds
   * every served module, but a module that server cannot serve gets one of its own, and a server whose
   * folder set went stale keeps running next to the new one until the restart completes.
   */
  fun getContext(lspClient: LspClient): PyreflyTypeEvalContext {
    return cachedMapStorage.value.getOrPut(lspClient) { PyreflyTypeEvalContext(lspClient) }
  }

  companion object {
    fun getInstance(project: Project): PyreflyTypeEvalContextCache = project.service()
  }
}