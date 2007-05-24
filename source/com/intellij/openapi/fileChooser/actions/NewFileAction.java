/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import com.intellij.ui.LayeredIcon;

public class NewFileAction extends FileChooserDialogImpl.FileChooserAction {
  private final FileType myFileType;
  private final String myInitialContent;

  public NewFileAction(FileSystemTree fileSystemTree, FileType fileType, String initialContent) {
    super(UIBundle.message("file.chooser.new.file.action.name"), UIBundle.message("file.chooser.new.file.action.description"),
          LayeredIcon.create(fileType.getIcon(), IconLoader.getIcon("/actions/new.png")), fileSystemTree, null);
    myFileType = fileType;
    myInitialContent = initialContent;
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
    createNewFolder(fileSystemTree, myFileType, myInitialContent);
  }

  private static void createNewFolder(FileSystemTree fileSystemTree, final FileType fileType, final String initialContent) {
    final VirtualFile file = fileSystemTree.getSelectedFile();
    if (file == null || !file.isDirectory()) return;

    String newFileName;
    while (true) {
      newFileName = Messages.showInputDialog(UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                                               UIBundle.message("new.file.dialog.title"), Messages.getQuestionIcon());
      if (newFileName == null) {
        return;
      }
      if ("".equals(newFileName.trim())) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      Exception failReason = ((FileSystemTreeImpl)fileSystemTree).createNewFile(file, newFileName, fileType, initialContent);
      if (failReason != null) {
        Messages.showMessageDialog(UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        continue;
      }
      return;
    }
  }
}