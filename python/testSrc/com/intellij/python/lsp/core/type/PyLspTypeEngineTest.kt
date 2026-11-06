// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core.type

import com.intellij.idea.TestFor
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.python.junit5Tests.framework.pyProjectFixture
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

@TestApplication
internal class PyLspTypeEngineTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val mainPath = tempPathFixture()
  private val mainPyProject = projectFixture.pyProjectFixture(mainPath)

  /**
   * A restart replaces the client, and a cached `TypeEvalContext` still holds this engine. A stopped
   * server answers nothing, so the engine is not ready. The engine still sees the element, so the
   * context does not give it to the built-in engine.
   */
  @Test
  @TestFor(issues = ["PY-92008", "PY-92254"])
  fun `an engine behind a stopped server is not ready`() = runBlocking {
    val stopped = fakePyToolClient(mainPyProject.get(), serverState = LspServerState.ShutdownNormally)
    val engine = FakeLspTypeEngine(stopped)

    assertFalse(engine.isReady)
    assertTrue(engine.supports(referenceIn(mainPath.get())))
  }

  /**
   * The platform answers `null` to every request sent before the server runs. A ready engine would
   * get `PyNullType` for the element, instead of the answer of the server after it starts.
   */
  @Test
  @TestFor(issues = ["PY-92008", "PY-92254"])
  fun `an engine behind an initializing server is not ready`() = runBlocking {
    val initializing = fakePyToolClient(mainPyProject.get(), serverState = LspServerState.Initializing)
    val engine = FakeLspTypeEngine(initializing)

    assertFalse(engine.isReady)
    assertTrue(engine.supports(referenceIn(mainPath.get())))
  }

  /** [PyLspTypeEngine.isSupportedForResolve] reads the PSI, so it runs in a read action. */
  private suspend fun PyLspTypeEngine.supports(element: PyTypedElement): Boolean =
    readAction { isSupportedForResolve(element) }

  /** A `PyReferenceExpression` in a new `main.py` under [directory]. */
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
    override val lspClient: LspClient,
  ) : PyLspTypeEngine {
    override val name: String = "fake"

    override fun resolveType(pyTypedElement: PyTypedElement, isUserInitiated: Boolean): Ref<PyType?>? = null
  }
}
