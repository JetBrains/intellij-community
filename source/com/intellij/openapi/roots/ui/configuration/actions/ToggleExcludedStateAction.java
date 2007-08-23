package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.ProjectBundle;

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
    templatePresentation.setText(ProjectBundle.message("module.toggle.excluded.action"));
    templatePresentation.setDescription(ProjectBundle.message("module.toggle.excluded.action.description"));
    templatePresentation.setIcon(IconSet.EXCLUDE_FOLDER);
  }

  public boolean isSelected(AnActionEvent e) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    if (selectedFiles == null || selectedFiles.length == 0) {
      return false;
    }
    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    return contentEntryEditor.isExcluded(selectedFiles[0]) || contentEntryEditor.isUnderExcludedDirectory(selectedFiles[0]);
  }

  public void setSelected(AnActionEvent e, boolean isSelected) {
    final VirtualFile[] selectedFiles = getSelectedFiles();
    assert selectedFiles != null && selectedFiles.length > 0;
    for (VirtualFile selectedFile : selectedFiles) {
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
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText(ProjectBundle.message("module.toggle.excluded.action"));
  }

}
