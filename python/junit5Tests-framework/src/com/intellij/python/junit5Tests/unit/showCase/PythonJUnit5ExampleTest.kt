// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.FolderTest
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.helper.doHighlighting
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@PyDefaultTestApplication
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath($$"$CONTENT_ROOT/../junit5Tests-framework/testResources/example/junit5")
class PythonJUnit5ExampleTest(
  val project: Project, /* class-level, the value is projectFixture.get(), might be declared implicitly */
  val module: Module, /* class-level implicitly declared in PyWithDefaultFixturesExtension */
) {
  companion object {
    // An explicit override of the default fixture, which can be omitted if the default is enough.
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  /**
   * test folder iteration is here [com.intellij.python.junit5Tests.framework.AllFilesInFolderTestCaseProvider.provideTestTemplateInvocationContexts]
   */
  @FolderTest // runs testMyFolderTest on each file in the '@TestDataPath/myFolderTest' folder, filtering by regex is supported
  fun testHighlighting(
    file: PsiFile, /* implicitly declared in PyWithDefaultFixturesExtension on the test method level */
    editor: Editor, /* implicitly declared in PyWithDefaultFixturesExtension on the test method level */
  ) {
    editor.doHighlighting()

    val doc = PsiDocumentManager.getInstance(project).getDocument(file)!!
    val markupModel = DocumentMarkupModel.forDocument(doc, project, false)
    val highlighters = markupModel.allHighlighters
    assert(highlighters.isNotEmpty())
  }

  /**
   * resource recognition by test name is here [com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfoData.getTestResourcePath]
   */
  @Test
  fun testSingle(
    psiFile: PsiFile, /* testMyTestName -> myTestName.* (should be a single file with this name) in the @TestDataPath folder */
  ) = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      Assertions.assertEquals("print(\"Hello, world!\")\n", psiFile.text)
    }
  }

  /**
   * This is an example of a parametrized test with multiple cases provided.
   * The one could write expectations among the parameters as well.
   *
   * @param fileName the name of the file being tested
   * @param length the expected length of the file name
   */
  @ParameterizedTest
  @CsvSource(value = [
    "first.py,8",
    "second.py,9",
  ])
  fun testMultipleParameters(fileName: String, length: Int) {
    Assertions.assertEquals(length, fileName.length)
  }
}