package com.intellij.openapi.roots.ui.configuration.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.roots.ui.configuration.ContentEntryEditor;
import com.intellij.openapi.roots.ui.configuration.ContentEntryTreeEditor;
import com.intellij.openapi.roots.ui.configuration.IconSet;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 * Date: Oct 14 2003
 */
public class ToggleSourcesStateAction extends ContentEntryEditingAction {
  private final ContentEntryTreeEditor myEntryTreeEditor;
  private final boolean myEditTestSources;

  public ToggleSourcesStateAction(JTree tree, ContentEntryTreeEditor entryEditor, boolean editTestSources) {
    super(tree);
    myEntryTreeEditor = entryEditor;
    myEditTestSources = editTestSources;
    final Presentation templatePresentation = getTemplatePresentation();
    if (editTestSources) {
      templatePresentation.setText("Test Sources");
      templatePresentation.setDescription("Mark directory as a Test Sources root");
      templatePresentation.setIcon(IconSet.TEST_ROOT_FOLDER);
    }
    else {
      templatePresentation.setText("Sources");
      templatePresentation.setDescription("Mark directory as a Sources root");
      templatePresentation.setIcon(IconSet.SOURCE_ROOT_FOLDER);
    }
  }

  public boolean isSelected(AnActionEvent e) {
    final VirtualFile selectedFile = getSelectedFile();
    if (selectedFile == null) {
      return false;
    }
    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    return myEditTestSources? contentEntryEditor.isTestSource(selectedFile) : contentEntryEditor.isSource(selectedFile);
  }

  public void setSelected(AnActionEvent e, boolean isSelected) {
    final VirtualFile selectedFile = getSelectedFile();
    if (selectedFile == null) {
      return;
    }
    final ContentEntryEditor contentEntryEditor = myEntryTreeEditor.getContentEntryEditor();
    final SourceFolder sourceFolder = contentEntryEditor.getSourceFolder(selectedFile);
    if (isSelected) {
      if (sourceFolder == null) { // not marked yet
        contentEntryEditor.addSourceFolder(selectedFile, myEditTestSources);
      }
      else {
        if (myEditTestSources? !sourceFolder.isTestSource() : sourceFolder.isTestSource()) {
          contentEntryEditor.removeSourceFolder(sourceFolder);
          contentEntryEditor.addSourceFolder(selectedFile, myEditTestSources);
        }
      }
    }
    else {
      if (sourceFolder != null) { // already marked
        contentEntryEditor.removeSourceFolder(sourceFolder);
      }
    }
  }

  public void update(AnActionEvent e) {
    super.update(e);
    final Presentation presentation = e.getPresentation();
    presentation.setText(myEditTestSources? "Test Sources" : "Sources");
  }
}
