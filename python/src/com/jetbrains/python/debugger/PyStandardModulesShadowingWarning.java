// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.sdk.PySdkUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Snyssfx
 */
public class PyStandardModulesShadowingWarning {
  private static final Logger LOG = Logger.getInstance("com.jetbrains.python.debugger.PyStandardModulesShadowingWarning");
  private static final String WARNING_GROUP_ID = "Shadowing standard modules";
  private static final String STD_LISTS_DIR = "python-stdlib-list/stdlib_list/lists";

  private static final String WARNING_TITLE = "Rename Some User Modules";

  private static final String WARNING_MESSAGE = "Shadowing modules of standard library might produce unexpected errors in " +
                                                "both the debugger and the user's code.";
  private static final String WARNING_MORE_INFO_MESSAGE = "Shadowing modules of standard library might produce unexpected errors in\n" +
                                                          "both the debugger and the user's code.\n\n" +
                                                          "This modules should be renamed:\n";
  private static HashSet<String> ourPyStdNames = new HashSet<>();

  public static void showStandartModulesShadowingWarning(@NotNull Project project) {
    if (shouldSuppressNotification(project)) {
      return;
    }

    if (ourPyStdNames.isEmpty()) {
      addStandardLibraries(project);
    }

    final HashMap<String, VirtualFile> pyModulesToFiles = getNamesToFiles(GlobalSearchScope.projectScope(project));
    HashSet<String> shadowingModules = new HashSet<>(pyModulesToFiles.keySet());
    shadowingModules.retainAll(ourPyStdNames);

    if (shadowingModules.isEmpty()) {
      return;
    }

    Notification notification = new Notification(
      WARNING_GROUP_ID, WARNING_TITLE, WARNING_MESSAGE, NotificationType.INFORMATION);
    notification.addAction(createMoreInfoAction(notification, project, pyModulesToFiles, shadowingModules));
    notification.notify(project);
  }

  private static boolean shouldSuppressNotification(@NotNull Project project) {
    final RunManager runManager = RunManager.getInstance(project);
    final RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
    if (selectedConfiguration == null) {
      return true;
    }
    final RunConfiguration configuration = selectedConfiguration.getConfiguration();
    if (!(configuration instanceof AbstractPythonRunConfiguration)) {
      return true;
    }
    AbstractPythonRunConfiguration runConfiguration = (AbstractPythonRunConfiguration)configuration;
    // Temporarily disable notification for Remote interpreters
    return PySdkUtil.isRemote(runConfiguration.getSdk());
  }

  private static void addStandardLibraries(@NotNull Project project) {

    final String listsDir = PythonHelpersLocator.getHelperPath(STD_LISTS_DIR);

    try {
      for (final File list : new File(listsDir).listFiles()) {

        try (BufferedReader reader = new BufferedReader(new FileReader(list))) {
          String line;
          while ((line = reader.readLine()) != null) {
            ourPyStdNames.add(line);
          }

        }
      }
    }
    catch (IOException | NullPointerException e) {
      showErrorDialog(project, "Cannot read standard library lists");
    }
  }

  private static void showErrorDialog(Project project, String message) {
    Messages.showMessageDialog(project, message, "ShadowingStdModulesError", null);
  }

  private static HashMap<String, VirtualFile> getNamesToFiles(GlobalSearchScope scope) {
    final Collection<VirtualFile> vFiles = FileTypeIndex.getFiles(PythonFileType.INSTANCE, scope);
    HashMap<String, VirtualFile> modulesToFiles = new HashMap<>();

    for (VirtualFile file : vFiles) {

      assert file.getPath().contains(".py");

      final String[] splittedPath = file.getPath().split("/");
      final String filename = splittedPath[splittedPath.length - 1];
      final String moduleName = filename.substring(0, filename.indexOf(".py"));

      modulesToFiles.put(moduleName, file);
    }
    return modulesToFiles;
  }

  private static AnAction createMoreInfoAction(@NotNull Notification notification, @NotNull Project project,
                                               @NotNull HashMap<String, VirtualFile> modulesToFiles,
                                               @NotNull HashSet<String> shadowingModules) {
    return new DumbAwareAction("More info") {

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        final String contentRoot = ProjectRootManager.getInstance(project).getContentRoots()[0].getPath();

        final StringBuilder msg = new StringBuilder(WARNING_MORE_INFO_MESSAGE);
        for (String module : shadowingModules) {

          final String path = modulesToFiles.get(module).getPath();
          final String relativePath = path.replace(contentRoot + "/", "");

          msg.append(relativePath).append("\n");
        }

        Messages.showWarningDialog(project, msg.toString(), WARNING_TITLE);
        notification.expire();
      }
    };
  }
}
