package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;

public final class TrailingSpacesStripper extends FileDocumentManagerAdapter{
  public void beforeDocumentSaving(final Document document) {
    if (!document.isWritable()) return;
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    if (editorSettings != null && !editorSettings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE)){
      final boolean inChangedLinesOnly = !editorSettings.getStripTrailingSpaces().equals(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
      CommandProcessor.getInstance().runUndoTransparentAction(
        new Runnable() {
          public void run() {
            ((DocumentEx)document).stripTrailingSpaces(inChangedLinesOnly);
          }
        }
      );
    }
  }
}
