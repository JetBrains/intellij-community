package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

public final class GotoProjectDirectory extends FileChooserDialogImpl.FileChooserAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileChooser.actions.GotoProjectDirectory");
  public GotoProjectDirectory(FileSystemTree fileSystemTree) {
    super("Project", "Go to project directory", IconLoader.getIcon("/nodes/ideaProject.png"), fileSystemTree);
  }

  protected void actionPerformed(FileSystemTree fileSystemTree, AnActionEvent e) {
    final VirtualFile projectPath = getProjectPath(e);
    LOG.assertTrue(projectPath != null);
    fileSystemTree.select(projectPath);
    fileSystemTree.expand(projectPath);
  }

  protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final VirtualFile projectPath = getProjectPath(e);
    presentation.setEnabled(projectPath != null && fileSystemTree.isUnderRoots(projectPath));
  }

  private static VirtualFile getProjectPath(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final VirtualFile projectFileDir = (VirtualFile)dataContext.getData(DataConstantsEx.PROJECT_FILE_DIRECTORY);
    if (projectFileDir == null || !projectFileDir.isValid()) {
      return null;
    }
    return projectFileDir;
  }

}
