// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.FileContentUtilCore
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyBuiltinCache::class])
class PyBuiltinCacheTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-92868"])
  fun `a reparse of the types stub gives a valid None type`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", "x = None")
    val oldTypesFile = runReadAction { PyBuiltinCache.getInstance(file).noneType!!.pyClass.containingFile }

    FileContentUtilCore.reparseFiles(oldTypesFile.virtualFile)

    runReadAction {
      val noneType = PyBuiltinCache.getInstance(file).noneType!!
      noneType.assertValid("NoneType")
      assertNotSame(oldTypesFile, noneType.pyClass.containingFile)
    }
  }

  @Test
  @TestFor(issues = ["PY-92674"])
  fun `builtin class type follows a change of the language level`(@TestDisposable disposable: Disposable) {
    val file = myFixture.configureByText("a.py", "")
    runReadActionBlocking {
      val cache = PyBuiltinCache.getInstance(file)
      assertNotNull(cache.getClass("frozendict"))
      assertNotNull(cache.getObjectType("frozendict"))
    }

    forceLanguageLevel(LanguageLevel.PYTHON314, disposable)

    runReadActionBlocking {
      val cache = PyBuiltinCache.getInstance(myFixture.psiManager.findFile(file.virtualFile)!!)
      assertNull(cache.getClass("frozendict"))
      assertNull(cache.getObjectType("frozendict"))
    }
  }

  private fun forceLanguageLevel(level: LanguageLevel, disposable: Disposable) {
    val project = myFixture.project
    val previous = LanguageLevel.FORCE_LANGUAGE_LEVEL
    PythonLanguageLevelPusher.setForcedLanguageLevel(project, level)
    Disposer.register(disposable) {
      PythonLanguageLevelPusher.setForcedLanguageLevel(project, previous)
      IndexingTestUtil.waitUntilIndexesAreReady(project)
    }
    IndexingTestUtil.waitUntilIndexesAreReady(project)
  }
}
