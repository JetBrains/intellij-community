// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Which [PyDebuggerBackendSwitchHandler] decides the backends that the switcher offers.
 *
 * A handler that does not own the context must not decide for it (PY-93339).
 */
@TestApplication
@Subsystems.Debugger
@Layers.Functional
@TestFor(issues = ["PY-93339"])
internal class PyDebuggerBackendSwitcherAvailableBackendsTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `only the owner decides`() {
    val stranger = FakeHandler(owns = false, backends = setOf(PyDebuggerBackend.PYDEVD))
    val owner = FakeHandler(owns = true, backends = null)
    register(stranger, owner)

    assertThat(getHandlersAvailableBackends(event())).isNull()
    assertThat(stranger.asked).isFalse()
    assertThat(owner.asked).isTrue()
  }

  @Test
  fun `without an owner no handler decides`() {
    val stranger = FakeHandler(owns = false, backends = setOf(PyDebuggerBackend.DEBUGPY))
    register(stranger)

    assertThat(getHandlersAvailableBackends(event())).isNull()
    assertThat(stranger.asked).isFalse()
  }

  private fun register(vararg handlers: PyDebuggerBackendSwitchHandler) {
    ExtensionTestUtil.maskExtensions(PyDebuggerBackendSwitchHandler.EP_NAME, handlers.toList(), disposable)
  }

  private fun event(): AnActionEvent =
    TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, projectFixture.get()))

  private class FakeHandler(private val owns: Boolean, private val backends: Set<PyDebuggerBackend>?) : PyDebuggerBackendSwitchHandler {
    var asked: Boolean = false
      private set

    override fun isApplicable(project: Project): Boolean = false

    override fun handleSessions(project: Project) = Unit

    override fun isDebugpyAvailableForSdk(project: Project, sdk: Sdk): Boolean = true

    override fun ownsRunProfile(e: AnActionEvent): Boolean = owns

    override fun getAvailableBackends(e: AnActionEvent): Set<PyDebuggerBackend>? {
      asked = true
      return backends
    }
  }
}
