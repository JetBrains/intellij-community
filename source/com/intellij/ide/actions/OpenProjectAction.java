package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Icons;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.awt.*;
import java.io.File;

public class OpenProjectAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    JFileChooser fileChooser = new JFileChooser(RecentProjectsManager.getInstance().getLastProjectPath());
    FileView fileView = new FileView() {
      public Icon getIcon(File f) {
        if (f.isDirectory()) return super.getIcon(f);
        if (f.isFile() && f.getName().toLowerCase().endsWith(".ipr")) {
          return Icons.PROJECT_ICON;
        }
        return super.getIcon(f);
      }
    };
    fileChooser.setFileView(fileView);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle("Open Project");
    fileChooser.setFileFilter(
      new FileFilter() {
        public boolean accept(File f) {
          return f.isDirectory() || f.getAbsolutePath().endsWith(".ipr");
        }

        public String getDescription() {
          return "Project files (*.ipr)";
        }
      }
    );
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    Window window = WindowManager.getInstance().suggestParentWindow(project);
    if (fileChooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
    File file = fileChooser.getSelectedFile();
    if (file == null) return;
    ProjectUtil.openProject(file.getAbsolutePath(), project, false);
  }
}