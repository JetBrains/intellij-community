// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.jetbrains.python.sdk

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.EditorNotifications
import com.intellij.ui.EditorNotificationsImpl
import com.jetbrains.extensions.getSdk
import com.jetbrains.python.fixtures.PyTestCase
import org.assertj.core.api.AssertionsForClassTypes
import javax.swing.JComponent

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
        val notificationPanel = getNotificationPanel(editor, fixture.project)
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

    private fun getNotificationPanel(fileEditor: FileEditor, project: Project): JComponent? {
      return (EditorNotifications.getInstance(project) as EditorNotificationsImpl).getNotificationPanels(fileEditor)
        .get(PyEditorNotificationProvider::class.java)
    }

    private fun openFileInEditor(fileName: String, fileText: String, fixture: CodeInsightTestFixture) : FileEditor {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      val editorNotifications = EditorNotifications.getInstance(fixture.project) as EditorNotificationsImpl
      editorNotifications.completeAsyncTasks()

      val psiFile: PsiFile = fixture.configureByText(fileName, fileText)
      val fileEditorManager = FileEditorManager.getInstance(fixture.project)
      val virtualFile = psiFile.virtualFile
      Disposer.register(fixture.testRootDisposable) {
        fileEditorManager.closeFile(virtualFile)
      }

      val editors = fileEditorManager.openFile(virtualFile, true)
      AssertionsForClassTypes.assertThat(editors).hasSize(1)

      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
      editorNotifications.completeAsyncTasks()

      return editors[0]
    }
  }
}