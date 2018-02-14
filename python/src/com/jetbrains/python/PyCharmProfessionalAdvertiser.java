// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.BrowserUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.sdk.PythonSdkType;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PyCharmProfessionalAdvertiser implements Annotator {


  @Language("HTML")
  private static final String NOTIFICATIONS_TEXT =
    "<a href=\"prof\">PyCharm Professional Edition</a> has special support for it.";

  private static final NotificationGroup BALLOON_NOTIFICATIONS = new NotificationGroup("PyCharm Professional Advertiser",
                                                                                       NotificationDisplayType.STICKY_BALLOON, false);
  private static final Key<Boolean> DONT_SHOW_BALLOON = Key.create("showingPyCompatibilityAdvertiserBalloon");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PyFile) {

      final PyFile pyFile = (PyFile)element;
      final Project project = element.getProject();

      final VirtualFile vFile = pyFile.getVirtualFile();
      if (vFile != null && FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
        return;
      }

      final Boolean showingFlag = project.getUserData(DONT_SHOW_BALLOON);
      if (showingFlag != null && showingFlag.booleanValue()) {
        return;
      }

      if (!moduleUsesPythonSdk(pyFile)) {
        return;
      }

      if (PyCellUtil.hasCells(pyFile)) {
        showInspectionAdvertisement(project, "code cells in editor");
      }

      if (containsImport(pyFile, "django")) {
        showInspectionAdvertisement(project, "Django Framework");
      }

      if (containsImport(pyFile, "flask")) {
        showInspectionAdvertisement(project, "Flask Framework");
      }

      if (containsImport(pyFile, "pyramid")) {
        showInspectionAdvertisement(project, "Pyramid Framework");
      }
    }
  }

  private static boolean moduleUsesPythonSdk(@NotNull PyFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file.getVirtualFile(), file.getProject());
    if (module != null) {
      return PythonSdkType.findPythonSdk(module) != null;
    }
    return false;
  }

  private static void showInspectionAdvertisement(@NotNull Project project, @NotNull String message) {
    showSingletonNotification(project, "You are using " + message, NOTIFICATIONS_TEXT, NotificationType.INFORMATION,
                              (notification, event) -> {
                                if ("prof".equals(event.getDescription())) {
                                  BrowserUtil.browse("https://www.jetbrains.com/pycharm/features/editions_comparison_matrix.html");
                                }
                              });
  }

  private static void showSingletonNotification(@NotNull Project project,
                                                @NotNull String title,
                                                @NotNull String htmlContent,
                                                @NotNull NotificationType type,
                                                @NotNull NotificationListener listener) {
    project.putUserData(DONT_SHOW_BALLOON, true);
    BALLOON_NOTIFICATIONS.createNotification(title, htmlContent, type, (notification, event) -> {
      try {
        listener.hyperlinkUpdate(notification, event);
      }
      finally {
        notification.expire();
      }
    }).notify(project);
  }

  private static boolean containsImport(@NotNull PyFile file, String pkg) {
    for (PyFromImportStatement importStatement : file.getFromImports()) {
      final QualifiedName name = importStatement.getImportSourceQName();
      if (name != null && name.toString().toLowerCase().contains(pkg)) {
        return true;
      }
    }
    for (PyImportElement importElement : file.getImportTargets()) {
      final QualifiedName name = importElement.getImportedQName();
      if (name != null && name.toString().toLowerCase().contains(pkg)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  private static PyCharmProfessionalAdvertiserSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, PyCharmProfessionalAdvertiserSettings.class);
  }
}
