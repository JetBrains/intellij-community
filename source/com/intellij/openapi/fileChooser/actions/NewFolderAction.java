package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;

public class NewFolderAction extends FileChooserDialogImpl.FileChooserAction {
  public NewFolderAction(FileSystemTree fileSystemTree) {
    super(UIBundle.message("file.chooser.new.folder.action.name"), UIBundle.message("file.chooser.new.folder.action.description"), IconLoader.getIcon("/actions/newFolder.png"), fileSystemTree, null);
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
      newFolderName = Messages.showInputDialog(UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                                               UIBundle.message("new.folder.dialog.title"), Messages.getQuestionIcon());
      if (newFolderName == null) {
        return;
      }
      if ("".equals(newFolderName.trim())) {
        Messages.showMessageDialog(UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFolder(file, newFolderName);
      if (failReason != null) {
        Messages.showMessageDialog(UIBundle.message("create.new.folder.could.not.create.folder.error.message", newFolderName),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}
