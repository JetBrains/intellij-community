/**
 * @author cdr
 */
package com.intellij.ide.actions;

import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ImportSettingsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Component component = (Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    final String path = ChooseComponentsToExportDialog.chooseSettingsFile(PathManager.getConfigPath(), component,
                                                                "Import File Location",
                                                                "Choose import file path or directory where the file located");
    if (path == null) return;

    final File saveFile = new File(path);
    try {
      if (!saveFile.exists()) {
        Messages.showErrorDialog("Cannot find file " + presentableFileName(saveFile), "File Not Found");
        return;
      }
      final ZipFile zipFile = new ZipFile(saveFile);

      final ZipEntry magicEntry = zipFile.getEntry(ExportSettingsAction.SETTINGS_JAR_MARKER);
      if (magicEntry == null) {
        Messages.showErrorDialog("The file " + presentableFileName(saveFile) +
                                 " contains no settings to import.\n" + promptLocationMessage(), "Invalid File");
        return;
      }

      final ArrayList<ExportableApplicationComponent> registeredComponents = new ArrayList<ExportableApplicationComponent>();
      final Map<File, Set<ExportableApplicationComponent>> filesToComponents = ExportSettingsAction.getRegisteredComponentsAndFiles(registeredComponents);
      List<ExportableApplicationComponent> components = getComponentsStored(saveFile, registeredComponents);
      final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(components, filesToComponents, false,
                                                                       "Select Components to Import",
                                                                       "Please check all components to import:");
      dialog.show();
      if (!dialog.isOK()) return;
      final Set<ExportableApplicationComponent> chosenComponents = dialog.getExportableComponents();
      Set<String> relativeNamesToExtract = new HashSet<String>();
      for (Iterator iterator = chosenComponents.iterator(); iterator.hasNext();) {
        ExportableApplicationComponent chosenComponent = (ExportableApplicationComponent)iterator.next();
        final File[] exportFiles = chosenComponent.getExportFiles();
        for (int j = 0; j < exportFiles.length; j++) {
          File exportFile = exportFiles[j];
          final File configPath = new File(PathManager.getConfigPath());
          final String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(configPath, exportFile));
          relativeNamesToExtract.add(relativePath);
        }
      }

      final File tempFile = new File(PathManagerEx.getPluginTempPath() + "/" + saveFile.getName());
      FileUtil.copy(saveFile, tempFile);
      File outDir = new File(PathManager.getConfigPath());
      final MyFilenameFilter filenameFilter = new MyFilenameFilter(relativeNamesToExtract);
      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(tempFile, outDir, filenameFilter);
      StartupActionScriptManager.addActionCommand(unzip);
      // remove temp file
      StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(tempFile);
      StartupActionScriptManager.addActionCommand(deleteTemp);

      final int ret = Messages.showOkCancelDialog("Settings imported successfully. You have to restart " +
                                                  ApplicationNamesInfo.getInstance().getProductName() + " to reload the settings." +
                                                  "\nShutdown " + ApplicationNamesInfo.getInstance().getFullProductName() + "?",
                                                  "Restart Needed", Messages.getQuestionIcon());
      if (ret == 0) {
        ApplicationManager.getApplication().exit();
      }
    }
    catch (ZipException e1) {
      Messages.showErrorDialog("Error reading file " + presentableFileName(saveFile) + ".\n" +
                               "There was " +e1.getMessage() +"\n\n" + promptLocationMessage(),
                               "Invalid File");
    }
    catch (IOException e1) {
      Messages.showErrorDialog("Error reading file " + presentableFileName(saveFile) + ".\n\n"+ e1.getMessage(), "Error Reading File");
    }
  }

  private static String presentableFileName(final File file) {
    return "'" + FileUtil.toSystemDependentName(file.getPath()) + "'";
  }

  private static String promptLocationMessage() {
    return "Please make sure you have generated the file using 'File|Export Settings' feature.";
  }

  private static List<ExportableApplicationComponent> getComponentsStored(File zipFile,
                                                                   ArrayList<ExportableApplicationComponent> registeredComponents)
    throws IOException {
    final File configPath = new File(PathManager.getConfigPath());

    final ArrayList<ExportableApplicationComponent> components = new ArrayList<ExportableApplicationComponent>();
    for (int i = 0; i < registeredComponents.size(); i++) {
      ExportableApplicationComponent component = registeredComponents.get(i);
      final File[] exportFiles = component.getExportFiles();
      for (int j = 0; j < exportFiles.length; j++) {
        File exportFile = exportFiles[j];
        String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(configPath, exportFile));
        if (exportFile.isDirectory()) relativePath += "/";
        if (ZipUtil.isZipContainsEntry(zipFile, relativePath)) {
          components.add(component);
          break;
        }
      }
    }
    return components;
  }

  private static class MyFilenameFilter implements FilenameFilter, Serializable {
    private final Set<String> myRelativeNamesToExtract;
    public MyFilenameFilter(Set<String> relativeNamesToExtract) {
      myRelativeNamesToExtract = relativeNamesToExtract;
    }

    public boolean accept(File dir, String name) {
      if (name.equals(ExportSettingsAction.SETTINGS_JAR_MARKER)) return false;
      final File configPath = new File(PathManager.getConfigPath());
      final String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(configPath, new File(dir, name)));
      for (Iterator iterator = myRelativeNamesToExtract.iterator(); iterator.hasNext();) {
        String allowedRelPath = (String)iterator.next();
        if (relativePath.startsWith(allowedRelPath)) return true;
      }
      return false;
    }
  }
}