// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.maven.testFramework.fixtures

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EditorTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.maven.utils.MavenLog
import org.junit.Assert.assertTrue

// Editor/PSI configuration: making a file the "current" editor file, typing, caret and element lookup.

suspend fun MavenDomTestFixture.configTest(f: VirtualFile) {
  if (Comparing.equal(configTimestamps[f], f.timeStamp)) {
    MavenLog.LOG.warn("MavenDomTestFixture configTest skipped")
    return
  }
  // Persist any unsaved in-memory document for this file (e.g. a pom edited by a JavaProjectModelModifier) before the
  // VFS refresh / re-configure below; otherwise MemoryDiskConflictResolver throws "unexpected memory-disk conflict in
  // tests", and re-reading from disk would also drop the in-memory edit the test is about to assert on.
  edtWriteAction {
    val fileDocumentManager = FileDocumentManager.getInstance()
    fileDocumentManager.getCachedDocument(f)?.let { document ->
      if (fileDocumentManager.isDocumentUnsaved(document)) fileDocumentManager.saveDocument(document)
    }
  }
  refreshFiles(listOf(f))
  awaitConfiguration()
  if (f.fileSystem is ArchiveFileSystem) {
    MavenLog.LOG.warn("MavenDomTestFixture configTest in ArchiveFileSystem skipped")
    return
  }
  fixture.configureFromExistingVirtualFile(f)
  configTimestamps[f] = f.timeStamp
}

suspend fun MavenDomTestFixture.type(f: VirtualFile, c: Char) {
  configTest(f)
  // EditorTestFixture.type(char) routes non-printable characters through a keymap shortcut lookup and silently falls
  // back to typing the character literally when the keystroke is unbound; the @TestApplication active keymap has no
  // Backspace binding, so type('\b') would insert a literal backspace char instead of deleting. Invoke the backspace
  // editor action directly against a full editor data context (keymap-independent). CodeInsightTestFixture.performEditorAction
  // is not used here because its EditorUtil.getEditorDataContext leaves the action reported as disabled in this headless
  // fixture, so it would silently no-op; EditorTestUtil.executeAction supplies EDITOR/PROJECT/VIRTUAL_FILE explicitly.
  if (c == '\b') {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        EditorTestUtil.executeAction(fixture.editor, IdeActions.ACTION_EDITOR_BACKSPACE, true)
      }
    }
  }
  else {
    fixture.type(c)
  }
}

suspend fun MavenDomTestFixture.getEditor(f: VirtualFile): Editor {
  configTest(f)
  return fixture.editor
}

/**
 * Executes an editor action against the current fixture editor. Unlike `CodeInsightTestFixture.performEditorAction`, which
 * builds its event from `EditorUtil.getEditorDataContext` and silently no-ops when the action is reported disabled in this
 * headless fixture (e.g. "GotoSuperMethod"), this routes through [EditorTestUtil.executeAction], which supplies a full
 * editor data context (EDITOR/PROJECT/VIRTUAL_FILE) and asserts the action is enabled.
 */
suspend fun MavenDomTestFixture.performEditorAction(actionId: String) {
  withContext(Dispatchers.EDT) {
    writeIntentReadAction {
      EditorTestUtil.executeAction(fixture.editor, actionId, true)
    }
  }
}

suspend fun MavenDomTestFixture.moveCaretTo(f: VirtualFile, textWithCaret: String) {
  val caretOffset = textWithCaret.indexOf("<caret>")
  assertTrue(caretOffset > 0)
  val textWithoutCaret = textWithCaret.replaceFirst("<caret>", "")
  val documentText = getEditor(f).document.text
  val textOffset = documentText.indexOf(textWithoutCaret)
  assertTrue(textOffset > 0)
  val offset = textOffset + caretOffset
  withContext(Dispatchers.EDT) {
    val editor = getEditor(f)
    writeIntentReadAction {
      editor.caretModel.moveToOffset(offset)
    }
  }
}

suspend fun MavenDomTestFixture.findPsiFile(f: VirtualFile?): PsiFile {
  return readAction { PsiManager.getInstance(project).findFile(f!!)!! }
}

suspend fun MavenDomTestFixture.getTestPsiFile(f: VirtualFile): PsiFile {
  configTest(f)
  return fixture.file
}

suspend fun MavenDomTestFixture.getElementAtCaret(f: VirtualFile): PsiElement? {
  configTest(f)
  val psiFile = findPsiFile(f)
  val editor = getEditor(f)
  return readAction { psiFile.findElementAt(editor.caretModel.offset) }
}
