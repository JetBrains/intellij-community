package com.intellij.ide.actions;

import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.fileTypes.ex.FileTypeChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.io.FileTypeFilter;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.io.File;

public class OpenFileAction extends AnAction {
  private static String getLastFilePath(Project project) {
    return PropertiesComponent.getInstance(project).getValue("last_opened_file_path");
  }

  private static void setLastFilePath(Project project, String path) {
    PropertiesComponent.getInstance(project).setValue("last_opened_file_path", path);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) return;

    String lastFilePath = getLastFilePath(project);
    String path = lastFilePath != null ? lastFilePath : RecentProjectsManager.getInstance().getLastProjectPath();
    JFileChooser fileChooser = new JFileChooser(path);
    FileView fileView = new FileView() {
      public Icon getIcon(File f) {
        if (f.isDirectory()) return super.getIcon(f);
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
        return fileType.getIcon();
      }
    };
    fileChooser.setFileView(fileView);
    fileChooser.setMultiSelectionEnabled(true);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle("Open File");

    FileFilter allFilesFilter = new FileFilter() {
      public boolean accept(File f) {
        return true;
      }

      public String getDescription() {
        return "All file types";
      }
    };

    fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.JAVA));
    fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.JSP));
    fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.XML));
    fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.HTML));
    fileChooser.addChoosableFileFilter(new FileTypeFilter(StdFileTypes.PLAIN_TEXT));
    fileChooser.addChoosableFileFilter(allFilesFilter);

    fileChooser.setFileFilter(allFilesFilter);

    if (fileChooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(project)) !=
        JFileChooser.APPROVE_OPTION) {
      return;
    }
    File [] files = fileChooser.getSelectedFiles();
    if (files == null) return;

    for (File file : files) {
      setLastFilePath(project, file.getParent());
      if (isProjectFile(file)) {
        int answer = Messages.showYesNoDialog(project,
                                              file.getName() + " is an " + ApplicationNamesInfo.getInstance().getProductName() +
                                                                       " project file.\nWould you like to open this project?",
                                              "Open Project",
                                              Messages.getQuestionIcon());
        if (answer == 0) {
          ProjectUtil.openProject(file.getAbsolutePath(), project, false);
          return;
        }
      }

      FileType type = FileTypeChooser.getKnownFileTypeOrAssociate(file.getName());
      if (type == null) return;

      String absolutePath = file.getAbsolutePath();
      openFile(absolutePath, project);
    }
  }

  public static void openFile(String absolutePath, final Project project) {
    String correctPath = absolutePath.replace(File.separatorChar, '/');
    final VirtualFile[] virtualFiles = new VirtualFile[1];
    final String correctPath1 = correctPath;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        virtualFiles[0] = LocalFileSystem.getInstance().refreshAndFindFileByPath(correctPath1);
      }
    });
    if (virtualFiles[0] == null) return;

    FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
    if (editorProviderManager.getProviders(project, virtualFiles[0]).length == 0) {
      Messages.showMessageDialog(project,
                                 "Files of this type cannot be opened in " + ApplicationNamesInfo.getInstance().getProductName(),
                                 "Cannot Open File",
                                 Messages.getErrorIcon());
      return;
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFiles[0]);
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public static boolean isProjectFile(File file) {
    return FileTypeManager.getInstance().getFileTypeByFileName(file.getName()) == StdFileTypes.IDEA_PROJECT;
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
  }
}
