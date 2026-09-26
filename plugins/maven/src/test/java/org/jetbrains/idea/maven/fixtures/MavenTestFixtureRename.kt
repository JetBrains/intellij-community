// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")
package org.jetbrains.idea.maven.fixtures

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.maven.testFramework.fixtures.MavenDomTestFixture
import com.intellij.maven.testFramework.fixtures.MavenTestFixture
import com.intellij.maven.testFramework.fixtures.assertUnorderedElementsAreEqual
import com.intellij.maven.testFramework.fixtures.getEditor
import com.intellij.maven.testFramework.fixtures.getTestPsiFile
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.PsiElementRenameHandler
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.refactoring.util.CommonRefactoringUtil.RefactoringErrorHintException
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.usages.UsageTargetUtil
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull

// Rename refactoring and find-usages helpers.
suspend fun MavenDomTestFixture.assertRenameResult(value: String, expectedXml: String, omitModelVersionTag: Boolean = false) {
  doRename(projectPom, value)
  assertEquals(createPomXml(expectedXml, omitModelVersionTag), getTestPsiFile(projectPom).text)
}

suspend fun MavenDomTestFixture.assertCannotRename() {
  val context = createRenameDataContext(projectPom, "new name")
  val handler = readAction { RenameHandlerRegistry.getInstance().getRenameHandler(context) }
  if (null == handler) return
  try {
    invokeRename(context, handler)
  }
  catch (e: RefactoringErrorHintException) {
    if (!e.message!!.startsWith("Cannot perform refactoring.")) {
      throw e
    }
  }
}

suspend fun MavenDomTestFixture.doRename(f: VirtualFile, value: String) {
  val context = createRenameDataContext(f, value)
  val renameHandler = readAction { RenameHandlerRegistry.getInstance().getRenameHandler(context) }
  assertNotNull(renameHandler)
  invokeRename(context, renameHandler!!)
}

suspend fun MavenDomTestFixture.doInlineRename(f: VirtualFile, value: String) {
  val context = createRenameDataContext(f, value)
  val renameHandler = readAction { RenameHandlerRegistry.getInstance().getRenameHandler(context) }
  assertNotNull(renameHandler)
  assertInstanceOf(renameHandler, VariableInplaceRenameHandler::class.java)
  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      CodeInsightTestUtil.doInlineRename(renameHandler as VariableInplaceRenameHandler?, value, fixture)
    }
  }
}

private suspend fun MavenDomTestFixture.invokeRename(context: DataContext, renameHandler: RenameHandler) {
  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      renameHandler.invoke(project, PsiElement.EMPTY_ARRAY, context)
    }
  }
}

private suspend fun MavenDomTestFixture.createRenameDataContext(f: VirtualFile, value: String?): DataContext {
  val editor = getEditor(f)
  val psiFile = getTestPsiFile(f)
  return CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink ->
    sink[CommonDataKeys.EDITOR] = editor
    sink[PsiElementRenameHandler.DEFAULT_NAME] = value
    sink.lazy(CommonDataKeys.PSI_FILE) { psiFile }
    sink.lazy(CommonDataKeys.PSI_ELEMENT) {
      TargetElementUtil.findTargetElement(
        editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    }
  }
}

suspend fun MavenDomTestFixture.assertSearchResultsInclude(file: VirtualFile, vararg expected: PsiElement?) {
  assertContainsElements(search(file), *expected)
}

suspend fun MavenDomTestFixture.assertSearchResults(file: VirtualFile, vararg expected: PsiElement?) {
  assertUnorderedElementsAreEqual(search(file), *expected)
}

suspend fun MavenDomTestFixture.search(file: VirtualFile): List<PsiElement> {
  val editor = getEditor(file)
  val psiFile = getTestPsiFile(file)
  return readAction {
    val psiElement = TargetElementUtil.findTargetElement(
      editor, TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    val targets = UsageTargetUtil.findUsageTargets(editor, psiFile, psiElement)
    val target = (targets?.firstOrNull() as? PsiElement2UsageTargetAdapter)?.element ?: return@readAction emptyList()
    ReferencesSearch.search(target).findAll().map { it.element }
  }
}

suspend fun MavenTestFixture.renameModule(oldName: String, newName: String) {
  val moduleManager = ModuleManager.getInstance(project)
  val module = moduleManager.findModuleByName(oldName)!!
  val modifiableModel = moduleManager.getModifiableModel()
  try {
    modifiableModel.renameModule(module, newName)
  }
  catch (e: ModuleWithNameAlreadyExists) {
    throw RuntimeException(e)
  }
  edtWriteAction {
    modifiableModel.commit()
    project.getMessageBus().syncPublisher(ModuleListener.TOPIC).modulesRenamed(project, listOf(module)) { oldName }
  }
}