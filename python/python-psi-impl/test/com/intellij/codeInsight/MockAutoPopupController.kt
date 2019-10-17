package com.intellij.codeInsight

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Condition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.concurrent.TimeUnit

class MockAutoPopupController: AutoPopupController() {
  override fun autoPopupMemberLookup(editor: Editor?, condition: Condition<in PsiFile>?) {
  }

  override fun autoPopupMemberLookup(editor: Editor?, completionType: CompletionType?, condition: Condition<in PsiFile>?) {
  }

  override fun scheduleAutoPopup(editor: Editor, completionType: CompletionType, condition: Condition<in PsiFile>?) {
  }

  override fun scheduleAutoPopup(editor: Editor?) {
  }

  override fun cancelAllRequests() {
  }

  override fun autoPopupParameterInfo(editor: Editor, highlightedMethod: PsiElement?) {
  }

  override fun waitForDelayedActions(timeout: Long, unit: TimeUnit) {
  }

  override fun dispose() {
  }
}