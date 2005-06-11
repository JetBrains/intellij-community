package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

public class NewFolderAction extends FileChooserDialogImpl.FileChooserAction {
  public NewFolderAction(FileSystemTree fileSystemTree) {
    super("New Folder...", "Create new folder", IconLoader.getIcon("/actions/newFolder.png"), fileSystemTree, null);
    registerCustomShortcutSet(
      ActionManager.getInstance().getAction("NewElement").getShortcutSet(),
      fileSystemTree.getTree()
    );
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    VirtualFile selectedFile = fileSystemTree.getSelectedFile();
    presentation.setEnabled(selectedFile != null && selectedFile.isDirectory());
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    createNewFolder(fileSystemTree);
  }

  private static void createNewFolder(FileSystemTree fileSystemTree) {
    final VirtualFile file = fileSystemTree.getSelectedFile();
    if (file == null || !file.isDirectory()) return;

    String newFolderName;
    while (true) {
      newFolderName = Messages.showInputDialog("Enter a new folder name:", "New Folder", Messages.getQuestionIcon());
      if (newFolderName == null) {
        return;
      }
      if ("".equals(newFolderName.trim())) {
        Messages.showMessageDialog("Folder name cannot be empty", "Error", Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFolder(file, newFolderName);
      if (failReason != null) {
        Messages.showMessageDialog("Could not create folder '" + newFolderName + "'", "Error", Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}
