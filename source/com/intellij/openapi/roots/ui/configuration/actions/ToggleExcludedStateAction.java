package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 14 2003
 */
public class ToggleExcludedStateAction extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;

  public ToggleExcludedStateAction(JTree tree, ContentEntryTreeEditor entryEditor) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    final Presentation templatePresentation = getTemplatePresentation();
    templatePresentation.setText("Excluded");
    templatePresentation.setDescription("Include/Exclude directory from module");
    templatePresentation.setIcon(IconSet.EXCLUDE_FOLDER);
  }

  public boolean isSelected(AnActionEvent e) {
    final VirtualFile selectedFile = getSelectedFile();
    if (selectedFile == null) {
      return false;
    }
    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    return contentEntryEditor.isExcluded(selectedFile) || contentEntryEditor.isUnderExcludedDirectory(selectedFile);
  }

  public void setSelected(AnActionEvent e, boolean isSelected) {
    final VirtualFile selectedFile = getSelectedFile();
    final ExcludeFolder excludeFolder = myEntryTreeEditor.getContentEntryEditor().getExcludeFolder(selectedFile);
    if (isSelected) {
      if (excludeFolder == null) { // not excluded yet
        myEntryTreeEditor.getContentEntryEditor().addExcludeFolder(selectedFile);
      }
    }
    else {
      if (excludeFolder != null) {
        myEntryTreeEditor.getContentEntryEditor().removeExcludeFolder(excludeFolder);
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText("Excluded");
  }

}
