// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.EditorNotificationsImpl
import com.intellij.util.ui.UIUtil
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.inspections.PyInterpreterInspection
import org.assertj.core.api.AssertionsForClassTypes

class PythonNoSdkEditorNotificationTest : PyTestCase() {
  fun testNoSdkForPythonFile() {
    doTestSdkNotificationPanel(myFixture, "test.py", "", true)
  }

  fun testSdkExistsForPythonFile() {
    doTestSdkNotificationPanel(myFixture, "test.py", "", false)
  }

  companion object {
    fun doTestSdkNotificationPanel(fixture: CodeInsightTestFixture, fileName: String, fileText: String, sdkPresent: Boolean) {
      val moduleSdk = fixture.module.getSdk()
      try {
        if (!sdkPresent) {
          ModuleRootModificationUtil.setModuleSdk(fixture.module, null)
        }

        val editor = openFileInEditor(fileName, fileText, fixture)
        val notificationPanel = getNotificationPanel(editor)
        if (!sdkPresent && notificationPanel == null) {
          fail("\"No SDK notification\" expected")
        }
        else if (sdkPresent && notificationPanel != null) {
          fail("Unexpected SDK configuration panel")
        }
      }
      finally {
        ModuleRootModificationUtil.setModuleSdk(fixture.module, moduleSdk)
      }
    }

    fun getNotificationPanel(fileEditor: FileEditor) = EditorNotificationsImpl.getNotificationPanels(fileEditor)[PyInterpreterInspection::class.java]

    fun openFileInEditor(fileName: String, fileText: String, fixture: CodeInsightTestFixture) : FileEditor {
      UIUtil.dispatchAllInvocationEvents()
      EditorNotificationsImpl.completeAsyncTasks()

      val psiFile: PsiFile = fixture.configureByText(fileName, fileText)
      val fileEditorManager = FileEditorManager.getInstance(fixture.project)
      val virtualFile = psiFile.virtualFile
      Disposer.register(fixture.testRootDisposable) {
        fileEditorManager.closeFile(virtualFile)
      }

      val editors = fileEditorManager.openFile(virtualFile, true)
      AssertionsForClassTypes.assertThat(editors).hasSize(1)

      UIUtil.dispatchAllInvocationEvents()
      EditorNotificationsImpl.completeAsyncTasks()

      return editors[0]
    }
  }
}