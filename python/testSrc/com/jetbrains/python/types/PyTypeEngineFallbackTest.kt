// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.PyAnyType
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.psi.types.TypeEvalContextImpl
import com.jetbrains.python.psi.types.engine.PyTypeEngine
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * How [TypeEvalContextImpl.getType] shares the work between an external engine and the built-in one.
 *
 * - An element the engine cannot see goes to the built-in engine, whatever the engine allows.
 * - A ready engine answers for an element it sees.
 * - An engine that is not ready either leaves the element to the built-in engine, or answers
 *   `Unknown` without a cache entry, so the same context answers again once the engine is ready.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
@TestFor(classes = [TypeEvalContextImpl::class], issues = ["PY-92265"])
class PyTypeEngineFallbackTest : PyCodeInsightTestCase() {
  @Test
  fun `a ready exclusive engine answers`() {
    withTypeEngine(TestTypeEngine(exclusive = true)) {
      test("""
        expr = 42
        #└ TYPE str
      """.trimIndent())
    }
  }

  @Test
  fun `an element the exclusive engine cannot see goes to the built-in engine`() {
    withTypeEngine(TestTypeEngine(exclusive = true, seesElements = false)) {
      test("""
        expr = 42
        #└ TYPE Literal[42]
      """.trimIndent())
    }
  }

  @Test
  fun `an engine that allows the fallback leaves the element to the built-in engine until it is ready`() {
    withTypeEngine(TestTypeEngine(exclusive = false, ready = false)) {
      test("""
        expr = 42
        #└ TYPE Literal[42]
      """.trimIndent())
    }
  }

  @Test
  fun `an exclusive engine that is not ready answers Unknown and caches nothing`() {
    val engine = TestTypeEngine(exclusive = true, ready = false)
    withTypeEngine(engine) {
      val file = myFixture.configureByText("a.py", "expr = 42") as PyFile
      runReadActionBlocking {
        val target = file.findTopLevelAttribute("expr")!!
        val context = TypeEvalContext.codeAnalysis(file.project, file)
        assertSame(PyAnyType.unknown, context.getType(target))

        engine.ready = true
        assertEquals(PyBuiltinCache.getInstance(file).strType, context.getType(target))
      }
    }
  }

  private fun withTypeEngine(typeEngine: PyTypeEngine, action: () -> Unit) {
    val disposable = Disposer.newDisposable()
    try {
      ExtensionTestUtil.maskExtensions(TYPE_ENGINE_PROVIDER_EP, listOf(TestTypeEngineProvider(typeEngine)), disposable)
      action()
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private class TestTypeEngineProvider(private val typeEngine: PyTypeEngine) : PyTypeEngineProvider {
    override fun createTypeEngine(module: Module): PyTypeEngine = typeEngine
  }

  /**
   * An engine that answers `str` for every element it sees. The statistics collector accepts the name
   * of a real engine alone.
   */
  private class TestTypeEngine(
    exclusive: Boolean,
    @Volatile var ready: Boolean = true,
    private val seesElements: Boolean = true,
  ) : PyTypeEngine {
    override val name: String = "pyrefly"
    override val allowsBuiltInTypeEngineFallbackWhenUnavailable: Boolean = !exclusive
    override val isReady: Boolean get() = ready

    override fun isSupportedForResolve(pyTypedElement: PyTypedElement): Boolean = seesElements

    override fun resolveType(pyTypedElement: PyTypedElement, isLibrary: Boolean, isUserInitiated: Boolean): Ref<PyType?> {
      return Ref.create(PyBuiltinCache.getInstance(pyTypedElement).strType)
    }
  }

  private companion object {
    val TYPE_ENGINE_PROVIDER_EP: ExtensionPointName<PyTypeEngineProvider> =
      ExtensionPointName.create("Pythonid.typeEvalTypeEngineProvider")
  }
}
