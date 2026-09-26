package com.intellij.python.lsp.core.type

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.python.lsp.core.isUsable
import com.intellij.python.lsp.core.pyServedModules
import com.jetbrains.python.psi.PyExpressionCodeFragment
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PyLspTypeEngine : PyTypeEngine {
  /** The module this engine answers for. */
  val module: Module

  /** The client whose server answers for [module]. One server can answer for several modules. */
  val lspClient: LspClient

  override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean {
    val realFile = pyTypedElement.containingFile?.originalFile ?: return false
    if (realFile is PyExpressionCodeFragment)
      return false

    // An injected fragment (`.. code-block:: python` in a docstring, Python inside a string literal, ...)
    // lives in a VirtualFileWindow over a DocumentWindow, which the LSP server never saw and which the
    // LSP coordinate API rejects outright. Notebooks are unaffected: Jupyter exposes its Python cells
    // through a template-language view provider over the real .ipynb document, not through injection.
    if (InjectedLanguageManager.getInstance(realFile.project).isInjectedFragment(realFile))
      return false

    // The server answers for the interpreters and the content roots of the modules it serves. A
    // file of any other module resolves to `Any` there, so it must not go to this server.
    // A file of no module goes to any server: a library file reaches this point, and so
    // does a scratch file or a file outside every content root.
    val fileModule = moduleOfFile(realFile)
    if (fileModule != null && fileModule !in lspClient.pyServedModules)
      return false

    // A restart replaces the client, and a cached `TypeEvalContext` still holds this engine and this
    // client. A stopped server answers nothing, and `TypeEvalContextImpl.getType` would then store
    // `PyNullType` instead of letting PyCharm infer the type itself, so every type of the file would
    // go unknown until the user edits it. Nothing that restarts a server moves the trackers that
    // drop the context cache.
    if (!lspClient.isUsable)
      return false

    val isSupportedTypesVisitor = LspIsSupportedTypesVisitor()
    pyTypedElement.accept(isSupportedTypesVisitor)
    return isSupportedTypesVisitor.isSupported
  }
}

private val FILE_MODULE_KEY = Key.create<CachedValue<Ref<Module?>>>("py.lsp.type.engine.file.module")

/**
 * The module of [file], cached on the file.
 *
 * [ModuleUtilCore.findModuleForFile] takes a read action and queries the file index, and
 * [PyLspTypeEngine.isSupportedForResolve] runs one time for each element on the type evaluation
 * path. The answer depends only on the file, so compute it one time for each file.
 *
 * The cache drops when the project roots change, and also on any structural change to the virtual
 * file system. Moving a file from one module's content root to another changes the answer without
 * changing the roots.
 */
@ApiStatus.Internal
fun moduleOfFile(file: PsiFile): Module? =
  CachedValuesManager.getCachedValue(file, FILE_MODULE_KEY) {
    CachedValueProvider.Result.create(
      Ref.create(ModuleUtilCore.findModuleForFile(file)),
      ProjectRootModificationTracker.getInstance(file.project),
      VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
    )
  }.get()
