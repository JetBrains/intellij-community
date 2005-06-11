package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;

/**
 * @author Vladimir Kondratyev
 */
public final class GotoHomeAction extends FileChooserDialogImpl.FileChooserAction{
  public GotoHomeAction(FileSystemTree fileSystemTree) {
    super("Home", "Go to home directory", IconLoader.getIcon("/nodes/homeFolder.png"), fileSystemTree, KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_MASK));
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    VirtualFile homeDirectory = getHomeDirectory();
    fileSystemTree.select(homeDirectory);
    fileSystemTree.expand(homeDirectory);
  }

  private static VirtualFile getHomeDirectory(){
    return LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home").replace('\\','/'));
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if(!presentation.isEnabled()){
      return;
    }
    final VirtualFile homeDirectory = getHomeDirectory();
    presentation.setEnabled(homeDirectory != null && (fileSystemTree).isUnderRoots(homeDirectory));
  }
}
