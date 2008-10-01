package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

public class AddManageFileAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project p = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile selectedFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);

    final MavenProjectsManager manager = MavenProjectsManager.getInstance(p);
    FileChooserDescriptor singlePomSelection = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        return super.isFileSelectable(file) && !manager.isManagedFile(file);
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        if (!file.isDirectory()
            && !file.getName().equals(MavenConstants.POM_XML)) return false;
        return super.isFileVisible(file, showHiddenFiles);
      }
    };
    
    FileChooserDialog dialog = FileChooserFactory.getInstance().createFileChooser(singlePomSelection, p);
    VirtualFile[] files = dialog.choose(selectedFile, p);
    if (files.length == 0) return;

    manager.addManagedFile(files[0]);
  }
}
