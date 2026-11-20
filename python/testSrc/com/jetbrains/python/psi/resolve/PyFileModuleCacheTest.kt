// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.types.TypeEvalContextImpl
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-94250"], classes = [PyBuiltinCache::class, TypeEvalContextImpl::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyFileModuleCacheTest : PyCodeInsightTestCase() {
  @Test
  fun `module cache invalidates when roots change`() {
    val file = myFixture.configureByText("module.py", "")
    val module = myFixture.module
    runReadActionBlocking {
      assertSame(module, ModuleUtilCore.findModuleForPsiElement(file))
      assertSame(module, findModuleForPsiElementCached(file))
    }

    ModuleRootModificationUtil.updateModel(module) { model ->
      model.contentEntries.forEach(model::removeContentEntry)
    }

    runReadActionBlocking {
      assertNull(ModuleUtilCore.findModuleForPsiElement(file))
      assertNull(findModuleForPsiElementCached(file))
    }
  }

  @Test
  fun `module cache uses current element after PSI change`() {
    val file = myFixture.addFileToProject("module.py", "def removed():\n  pass") as PyFile
    val module = myFixture.module
    val contentRoot = runReadActionBlocking { file.virtualFile.parent }
    ModuleRootModificationUtil.addContentRoot(module, contentRoot)
    val removedFunction = runReadActionBlocking { file.findTopLevelFunction("removed")!! }
    runReadActionBlocking {
      assertSame(module, ModuleUtilCore.findModuleForPsiElement(removedFunction))
      findModuleForPsiElementCached(removedFunction)
    }

    val document = runReadActionBlocking { PsiDocumentManager.getInstance(myFixture.project).getDocument(file)!! }
    runInEdtAndWait {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        document.setText("class Current:\n  pass")
        PsiDocumentManager.getInstance(myFixture.project).commitDocument(document)
      }
    }

    runReadActionBlocking { assertFalse(removedFunction.isValid) }
    val currentClass = runReadActionBlocking { file.findTopLevelClass("Current")!! }
    val currentModule = runReadActionBlocking {
      assertSame(module, ModuleUtilCore.findModuleForPsiElement(currentClass))
      findModuleForPsiElementCached(currentClass)
    }
    assertSame(module, currentModule)
  }
}