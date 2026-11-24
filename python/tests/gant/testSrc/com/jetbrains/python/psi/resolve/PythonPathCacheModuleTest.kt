// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.idea.TestFor
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.util.QualifiedName
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** A module keeps its path cache in its user data. A dependency change drops the cache, and the cache ends with the module. */
@TestFor(classes = [PythonPathCache::class], issues = ["PY-94287", "PY-94273"])
@TestApplication
@Subsystems.CodeInsight
@Layers.Functional
internal class PythonPathCacheModuleTest {
  private val tempDir = tempPathFixture()
  private val projectFixture = projectFixture()

  private val cachedName = QualifiedName.fromComponents("cached")

  @Test
  fun `a module gives one cache until a dependency changes`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val module = edtWriteAction { ModuleManager.getInstance(project).newModule(tempDir.get().resolve("app.iml"), "PYTHON_MODULE") }
    val cache = readAction { PythonPathCache.getInstance(module) }
    cache.put(cachedName, emptyList())
    assertSame(cache, readAction { PythonPathCache.getInstance(module) })

    PyResolveCachesInvalidator.getInstance(project).dropPathCaches()

    val next = readAction { PythonPathCache.getInstance(module) }
    assertNotSame(cache, next)
    assertNull(next.get(cachedName))
  }

  @Test
  fun `a disposed module gives a cache`(): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val module = edtWriteAction { ModuleManager.getInstance(project).newModule(tempDir.get().resolve("gone.iml"), "PYTHON_MODULE") }
    edtWriteAction { ModuleManager.getInstance(project).disposeModule(module) }

    val cache = readAction { PythonPathCache.getInstance(module) }
    cache.put(cachedName, emptyList())
    assertNotNull(cache.get(cachedName))
  }
}
