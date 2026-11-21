// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.defaultProjectAwareService

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.defaultProjectAwareService.PyModuleServiceFactoryImpl
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestFor(classes = [PyModuleServiceFactoryImpl::class])
@Subsystems.CodeInsight
@Layers.Functional
@TestApplication
internal class PyModuleServiceFactoryImplTest {
  private val projectFixture = projectFixture()

  @Test
  fun `a module keeps one service`() {
    val module = newModule("live")
    val factory = PyModuleServiceFactoryImpl { Any() }

    assertSame(factory.getService(module), factory.getService(module))
  }

  @Test
  @TestFor(issues = ["PY-92921"])
  fun `a disposed module gets a service that is not cached`() {
    val module = newModule("disposed")
    val factory = PyModuleServiceFactoryImpl { Any() }
    val serviceOfLiveModule = factory.getService(module)
    runInEdtAndWait { runWriteAction { ModuleManager.getInstance(projectFixture.get()).disposeModule(module) } }

    val first = factory.getService(module)
    assertNotSame(serviceOfLiveModule, first)
    assertNotSame(first, factory.getService(module))
  }

  private fun newModule(name: String): Module = runInEdtAndGet {
    runWriteAction {
      ModuleManager.getInstance(projectFixture.get()).newNonPersistentModule(name, ModuleTypeManager.getInstance().defaultModuleType.id)
    }
  }
}
