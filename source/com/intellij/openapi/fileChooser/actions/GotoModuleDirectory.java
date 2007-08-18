package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class GotoModuleDirectory extends FileChooserDialogImpl.FileChooserAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.GotoProjectDirectory");
  private final Module myModuleToGo;

  public GotoModuleDirectory(FileSystemTree fileSystemTree, @Nullable Module moduleToGo) {
    super(UIBundle.message("file.chooser.goto.module.action.name"), UIBundle.message("file.chooser.goto.module.action.description"), IconLoader.getIcon("/nodes/ModuleClosed.png"), fileSystemTree, KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.CTRL_MASK));
    myModuleToGo = moduleToGo;
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    final VirtualFile path = getModulePath(e);
    LOG.assertTrue(path != null);
    fileSystemTree.select(path);
    fileSystemTree.expand(path);
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final VirtualFile path = getModulePath(e);
    presentation.setEnabled(path != null && fileSystemTree.isUnderRoots(path));
  }

  private VirtualFile getModulePath(AnActionEvent e) {
    Module module = myModuleToGo;
    if (module == null) {
      module = (Module)e.getDataContext().getData(DataConstants.MODULE_CONTEXT);
    }
    if (module == null) {
      module = DataKeys.MODULE.getData(e.getDataContext());
    }
    if (module == null) {
      return null;
    }
    final VirtualFile moduleFile = validated(module.getModuleFile());
    return (moduleFile != null)? validated(moduleFile.getParent()) : null;
  }

  private VirtualFile validated(final VirtualFile file) {
    if (file == null || !file.isValid()) {
      return null;
    }
    return file;
  }

}
