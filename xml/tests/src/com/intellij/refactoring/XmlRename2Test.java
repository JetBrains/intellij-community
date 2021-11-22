// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaOwner;

/**
 * @author spleaner
 */
public class XmlRename2Test extends XmlRenameTestCase {
  public void testRenameAndUndo_IDEADEV_11439() throws Exception {
    final FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    try {
      final String testName = getTestName(false);

      final PsiElement[] element = new PsiElement[1];

      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);

      final VirtualFile file = findVirtualFile(testName + ".xml");
      configureByFiles(null, file);

      CommandProcessor.getInstance().executeCommand(myProject, () -> {
        try {
          element[0] = TargetElementUtil.findTargetElement(myEditor, TargetElementUtil
                                                                       .ELEMENT_NAME_ACCEPTED |
                                                                     TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);

          performAction("anothertag");
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }, null, null, UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION);

      documentManager.doPostponedOperationsAndUnblockDocument(myEditor.getDocument());

      final UndoManager undoManager = UndoManager.getInstance(getProject());
      final FileEditor selectedEditor = editorManager.getSelectedEditor(editorManager.getSelectedFiles()[0]);

      assertTrue(undoManager.isUndoAvailable(selectedEditor));
      undoManager.undo(selectedEditor);

      documentManager.commitAllDocuments();

      final String newName = ((PsiMetaOwner)element[0]).getMetaData().getName();
      assertEquals("name should rollback after undo!", "note", newName);
    }
    finally {
      final VirtualFile[] selectedFiles = editorManager.getSelectedFiles();
      if (selectedFiles.length > 0) editorManager.closeFile(selectedFiles[0]);
    }
  }

}
