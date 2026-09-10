// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.type

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.lsp.core.fakePyToolClient
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * An LSP server answers for the interpreters and the content roots of the modules it serves, so an
 * engine must refuse an element of any other module. A file of no module goes to any server. A
 * library file reaches that case, and so does a file outside every content root.
 */
@TestApplication
@TestFor(issues = ["PY-91402"])
internal class PyLspTypeEngineTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val mainPath = tempPathFixture()
  private val secondPath = tempPathFixture()
  private val outsidePath = tempPathFixture()
  private val mainModule = projectFixture.pyModuleFixture(mainPath, addPathToSourceRoot = true)
  private val secondModule = projectFixture.pyModuleFixture(secondPath, addPathToSourceRoot = true)

  @Test
  fun `an element of a served module is supported`() = runBlocking {
    val engine = engineServing(mainModule.get())

    assertTrue(engine.isSupportedForResolve(referenceIn(mainPath.get())))
  }

  @Test
  fun `an element of a module the server does not serve is not supported`() = runBlocking {
    val engine = engineServing(mainModule.get())
    secondModule.get()

    assertFalse(engine.isSupportedForResolve(referenceIn(secondPath.get())))
  }

  @Test
  fun `one server that serves both modules answers for both`() = runBlocking {
    val engine = engineServing(mainModule.get(), secondModule.get())

    assertTrue(engine.isSupportedForResolve(referenceIn(mainPath.get())))
    assertTrue(engine.isSupportedForResolve(referenceIn(secondPath.get())))
  }

  @Test
  fun `the answer does not change when it is asked twice`() = runBlocking {
    val engine = engineServing(mainModule.get())
    secondModule.get()
    val ownReference = referenceIn(mainPath.get())
    val otherReference = referenceIn(secondPath.get())

    repeat(2) {
      assertTrue(engine.isSupportedForResolve(ownReference))
      assertFalse(engine.isSupportedForResolve(otherReference))
    }
  }

  @Test
  fun `an element of no module is supported`() = runBlocking {
    val engine = engineServing(mainModule.get())
    secondModule.get()

    assertTrue(engine.isSupportedForResolve(referenceIn(outsidePath.get())))
  }

  /**
   * A restart replaces the client, and a cached `TypeEvalContext` still holds this engine. A stopped
   * server answers nothing, so the engine must refuse, and PyCharm then infers the type itself.
   */
  @Test
  @TestFor(issues = ["PY-92008"])
  fun `an engine behind a stopped server supports nothing`() = runBlocking {
    val stopped = fakePyToolClient(mainModule.get(), serverState = LspServerState.ShutdownNormally)
    val engine = FakeLspTypeEngine(mainModule.get(), stopped)

    assertFalse(engine.isSupportedForResolve(referenceIn(mainPath.get())))
  }

  /**
   * The platform answers `null` to every request sent before the server runs. An engine that accepted
   * the element would store `PyNullType` for it, instead of letting PyCharm infer the type itself.
   */
  @Test
  @TestFor(issues = ["PY-92008"])
  fun `an engine behind an initializing server supports nothing`() = runBlocking {
    val initializing = fakePyToolClient(mainModule.get(), serverState = LspServerState.Initializing)
    val engine = FakeLspTypeEngine(mainModule.get(), initializing)

    assertFalse(engine.isSupportedForResolve(referenceIn(mainPath.get())))
  }

  /** An engine for the first of [servedModules], behind a server that answers for all of them. */
  private fun engineServing(vararg servedModules: Module): FakeLspTypeEngine =
    FakeLspTypeEngine(servedModules.first(), fakePyToolClient(*servedModules))

  /** A `PyReferenceExpression` in a new `main.py` under [directory]. [LspIsSupportedTypesVisitor] accepts it. */
  private suspend fun referenceIn(directory: Path): PyReferenceExpression {
    val file = directory.resolve("main.py")
    withContext(Dispatchers.IO) {
      file.writeText("value = len(\"a\")\n")
    }
    val virtualFile = withContext(Dispatchers.IO) {
      requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(file)) { "No virtual file for $file" }
    }
    return readAction {
      val psiFile = requireNotNull(PsiManager.getInstance(projectFixture.get()).findFile(virtualFile)) { "No PSI for $file" }
      requireNotNull(PsiTreeUtil.findChildOfType(psiFile, PyReferenceExpression::class.java)) {
        "No reference expression in ${psiFile.name}, which parsed as ${psiFile.language}"
      }
    }
  }

  private class FakeLspTypeEngine(
    override val module: Module,
    override val lspClient: LspClient,
  ) : PyLspTypeEngine {
    override val name: String = "fake"

    override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?>? = null
  }
}
