package com.intellij.openapi.editorActions

import com.intellij.codeInsight.editorActions.TabOutScopesTracker
import com.intellij.openapi.editor.Editor

class MockTabOutScopesTracker: TabOutScopesTracker {
  override fun registerEmptyScope(editor: Editor, offset: Int, tabOutOffset: Int) {
  }

  override fun hasScopeEndingAt(editor: Editor, offset: Int): Boolean = false

  override fun removeScopeEndingAt(editor: Editor, offset: Int): Int = 0
}