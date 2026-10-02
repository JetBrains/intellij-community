// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.python.ty.TyLspClientDescriptor
import com.intellij.python.ty.TyPyTool
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * [PyLspToolIntegrationProvider.attach] subscribes to the project changes one time for each project. `fileOpened`
 * calls it in a read action that a write action can cancel, so a cancelled subscription must not count as done.
 */
@TestApplication
@TestFor(classes = [PyLspToolIntegrationProvider::class], issues = ["PY-86537"])
internal class PyLspToolAttachTest {
  private val projectPath = tempPathFixture(prefix = "project")
  private val projectFixture = projectFixture(projectPath, openAfterCreation = true)
  private val moduleFixture = projectFixture.pyModuleFixture(projectPath, addPathToSourceRoot = true)

  @Test
  fun `a cancelled subscription subscribes again on the next attach`() {
    val provider = CancelOnceProvider()
    val descriptor = TyLspClientDescriptor(moduleFixture.get())

    assertThrows<ProcessCanceledException> { provider.attach(descriptor) }
    provider.attach(descriptor)
    provider.attach(descriptor)

    assertEquals(2, provider.subscriptions, "the second attach subscribes again, and the third one finds the subscription")
  }

  /** Throws a cancellation from its first subscription, as the first creation of a service in a cancelled read action does. */
  private class CancelOnceProvider : PyLspToolIntegrationProvider() {
    var subscriptions: Int = 0

    override fun getDescriptor(module: Module): PyLspToolDescriptor = TyLspClientDescriptor(module)

    override fun pyTool(project: Project): PyLspTool<*> = TyPyTool.getInstance()

    override fun subscribeOnChanges(pyTool: PyLspTool<*>, project: Project, parentDisposable: Disposable) {
      subscriptions++
      if (subscriptions == 1) throw ProcessCanceledException()
    }
  }
}
