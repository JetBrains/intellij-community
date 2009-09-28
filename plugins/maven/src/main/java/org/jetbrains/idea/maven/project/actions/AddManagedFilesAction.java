package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Arrays;

public class AddManagedFilesAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final MavenProjectsManager manager = MavenActionUtil.getProjectsManager(e);
    FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, false, false, false, false, true) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) && !manager.isManagedFile(file);
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!file.isDirectory() && !MavenActionUtil.isMavenProjectFile(file)) return false;
        return super.isFileVisible(file, showHiddenFiles);
      }
    };

    Project project = MavenActionUtil.getProject(e);
    VirtualFile fileToSelect = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(singlePomSelection, project);
    VirtualFile[] files = dialog.choose(fileToSelect, project);
    if (files.length == 0) return;

    manager.addManagedFiles(Arrays.asList(files));
  }
}
