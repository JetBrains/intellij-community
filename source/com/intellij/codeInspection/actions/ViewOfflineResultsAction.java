/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 23, 2002
 * Time: 7:14:29 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.offlineViewer.OfflineViewerHandler;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.application.options.ExpandMacroToPathMap;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Icons;
import org.jdom.Document;
import org.jdom.JDOMException;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileView;
import java.io.File;
import java.io.IOException;

public class ViewOfflineResultsAction extends AnAction {

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    presentation.setEnabled(project != null);
    presentation.setVisible(ActionPlaces.MAIN_MENU.equals(event.getPlace()));
  }

  public void actionPerformed(AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);

    String lastFilePath=getLastFilePath(project);
    String path = lastFilePath != null ? lastFilePath : RecentProjectsManager.getInstance().getLastProjectPath();
    JFileChooser fileChooser = new JFileChooser(path);
    FileView fileView = new FileView() {
      public Icon getIcon(File f) {
        if (f.isDirectory()) return super.getIcon(f);
        if (f.getName().endsWith(".ipr")) {
          return Icons.PROJECT_ICON;
        }
        FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
        return fileType.getIcon();
      }
    };

    fileChooser.setFileView(fileView);
    fileChooser.setAcceptAllFileFilterUsed(false);
    fileChooser.setDialogTitle("Open File");

    class TypeFilter extends FileFilter {
      private FileType myType;

      
      public TypeFilter() {
        myType = StdFileTypes.XML;
        myDescription = myType.getDescription();
      }

      public boolean accept(File f) {
        if (f.isDirectory()) return true;
        FileType type = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
        return myType == type;
      }

      public String getDescription() {
        return myDescription;
      }

      private String myDescription;
    }

    fileChooser.addChoosableFileFilter(new TypeFilter());

    if (fileChooser.showOpenDialog(WindowManager.getInstance().suggestParentWindow(project)) != JFileChooser.APPROVE_OPTION) return;
    File file = fileChooser.getSelectedFile();
    if (file == null) return;
    setLastFilePath(project, file.getParent());

    Document doc;
    try {
      doc = JDOMUtil.loadDocument(file);
      ((ProjectEx) project).getExpandMacroReplacements().substitute(doc.getRootElement(), SystemInfo.isFileSystemCaseSensitive);
    } catch (JDOMException e) {
      Messages.showMessageDialog(project, "Error parsing the results file", "Error", Messages.getErrorIcon());
      return;
    } catch (IOException e) {
      Messages.showMessageDialog(project, "Error loading the results file", "Error", Messages.getErrorIcon());
      return;
    }

    new OfflineViewerHandler(project).execute(doc);
  }

  private static String getLastFilePath(Project project) {
    return PropertiesComponent.getInstance(project).getValue("last_opened_file_path");
  }

  private static void setLastFilePath(Project project,String path) {
    PropertiesComponent.getInstance(project).setValue("last_opened_file_path",path);
  }
}
