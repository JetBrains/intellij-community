/**
 * @author cdr
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarOutputStream;

public class ExportSettingsAction extends AnAction {
  static final String SETTINGS_JAR_MARKER = "IntelliJ IDEA Global Settings";

  public void actionPerformed(AnActionEvent e) {
    List<ExportableApplicationComponent> exportableComponents = new ArrayList<ExportableApplicationComponent>();
    Map<File,Set<ExportableApplicationComponent>> fileToComponents = getRegisteredComponentsAndFiles(exportableComponents);

    final ChooseComponentsToExportDialog dialog = new ChooseComponentsToExportDialog(exportableComponents, fileToComponents, true,
                                                                     "Select Components to Export",
                                                                     "Please check all components to export:");
    dialog.show();
    if (!dialog.isOK()) return;
    Set<ExportableApplicationComponent> markedComponents = dialog.getExportableComponents();
    if (markedComponents.size() == 0) return;
    Set<File> exportFiles = new HashSet<File>();
    for (Iterator iterator = markedComponents.iterator(); iterator.hasNext();) {
      ExportableApplicationComponent component = (ExportableApplicationComponent)iterator.next();
      final File[] files = component.getExportFiles();
      exportFiles.addAll(Arrays.asList(files));
    }

    ApplicationManager.getApplication().saveSettings();

    try {
      final File saveFile = dialog.getExportFile();
      if (saveFile.exists()) {
        final int ret = Messages.showOkCancelDialog("Overwrite '"+FileUtil.toSystemDependentName(saveFile.getPath())+"'?",
                                                    "File Already Exists", Messages.getWarningIcon());
        if (ret != 0) return;
      }
      final JarOutputStream output = new JarOutputStream(new FileOutputStream(saveFile));
      final File configPath = new File(PathManager.getConfigPath());
      final HashSet<String> writtenItemRelativePaths = new HashSet<String>();
      for (Iterator<File> iterator = exportFiles.iterator(); iterator.hasNext();) {
        File file = iterator.next();
        final String relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(configPath, file));
        if (file.exists()) {
          ZipUtil.addFileOrDirRecursively(output, saveFile, file, relativePath, null, writtenItemRelativePaths);
        }
      }
      final File magicFile = new File(FileUtil.getTempDirectory(), SETTINGS_JAR_MARKER);
      magicFile.createNewFile();
      magicFile.deleteOnExit();
      ZipUtil.addFileToZip(output, magicFile, SETTINGS_JAR_MARKER, writtenItemRelativePaths, null);
      output.close();
      Messages.showMessageDialog("Settings exported successfully.\n" +
                                 "You can import the settings using 'File|Import Settings' feature.",
                                 "Export Successful", Messages.getInformationIcon());
    }
    catch (IOException e1) {
      Messages.showErrorDialog("Error writing settings.\n\n"+e1.toString(),"Error Writing File");
    }
  }

  public static Map<File, Set<ExportableApplicationComponent>> getRegisteredComponentsAndFiles(List<ExportableApplicationComponent> exportableComponents) {
    final Class[] interfaces = ApplicationManager.getApplication().getComponentInterfaces();
    Map<File,Set<ExportableApplicationComponent>> fileToComponents = new HashMap<File, Set<ExportableApplicationComponent>>();
    for (int i = 0; i < interfaces.length; i++) {
      final Class anInterface = interfaces[i];
      final Object component = ApplicationManager.getApplication().getComponent(anInterface);
      if (component instanceof ExportableApplicationComponent) {
        ExportableApplicationComponent exportable = (ExportableApplicationComponent)component;
        exportableComponents.add(exportable);
        final File[] exportFiles = exportable.getExportFiles();
        for (int j = 0; j < exportFiles.length; j++) {
          File exportFile = exportFiles[j];
          Set<ExportableApplicationComponent> componentsTied = fileToComponents.get(exportFile);
          if (componentsTied == null) {
            componentsTied = new HashSet<ExportableApplicationComponent>();
            fileToComponents.put(exportFile, componentsTied);
          }
          componentsTied.add(exportable);
        }
      }
    }
    return fileToComponents;
  }
}