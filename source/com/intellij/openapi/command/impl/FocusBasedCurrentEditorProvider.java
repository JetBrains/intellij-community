package com.intellij.openapi.command.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.fileEditor.FileEditor;

/**
 * @author max
 */
class FocusBasedCurrentEditorProvider implements CurrentEditorProvider {
  public FileEditor getCurrentEditor() {
    return (FileEditor)DataManager.getInstance().getDataContext().getData(DataConstants.FILE_EDITOR_NO_COMMIT);
  }
}