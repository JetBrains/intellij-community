// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.ide.BrowserUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.NlsContexts.NotificationContent;
import com.intellij.openapi.util.NlsContexts.NotificationTitle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PyCharmProfessionalAdvertiser implements Annotator {
  private static final NotificationGroup BALLOON_NOTIFICATIONS = new NotificationGroup("PyCharm Professional Advertiser",
                                                                                       NotificationDisplayType.STICKY_BALLOON, false);
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {

    final Project project = element.getProject();
    if (getSettings(project).shown) {
      return;
    }

    if (element instanceof PyFile) {

      final PyFile pyFile = (PyFile)element;
      final VirtualFile vFile = pyFile.getVirtualFile();
      if (vFile != null && FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
        return;
      }

      if (PyCellUtil.hasCells(pyFile)) {
        showInspectionAdvertisement(project,
                                    PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.title.cells.in.editor"),
                                    "https://www.jetbrains.com/pycharm/features/scientific_tools.html", "codecells");
      }

      if (PyPsiUtils.containsImport(pyFile, "django")) {
        showInspectionAdvertisement(project,
                                    PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.title.django.framework"),
                                    "https://www.jetbrains.com/pycharm/features/web_development.html#django", "django");
      }

      if (PyPsiUtils.containsImport(pyFile, "flask")) {
        showInspectionAdvertisement(project,
                                    PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.title.flask.framework"),
                                    null, "flask");
      }

      if (PyPsiUtils.containsImport(pyFile, "pyramid")) {
        showInspectionAdvertisement(project,
                                    PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.title.pyramid.framework"),
                                    null, "pyramid");
      }
    }

    if (isJupyterFile(element)) {
      showInspectionAdvertisement(element.getProject(),
                                  PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.title.jupyter.notebook"),
                                  "https://www.jetbrains.com/pycharm/features/scientific_tools.html", "jupyter");
    }
  }

  private static void showInspectionAdvertisement(@NotNull Project project,
                                                  @NotNull @NotificationTitle String message,
                                                  @Nullable String url,
                                                  @NotNull String source) {
    showSingletonNotification(project, message,
                              PyCharmCommunityCustomizationBundle.message("pro.advertiser.notification.pycharm.pro.has.support.for.it"),
                              NotificationType.INFORMATION,
                              (notification, event) -> {
                                if ("prof".equals(event.getDescription())) {
                                  BrowserUtil.browse(
                                    (url != null ? url : "https://www.jetbrains.com/pycharm/features/editions_comparison_matrix.html") +
                                    "?utm_source=from_product&utm_medium=advertiser&utm_campaign=" +
                                    source);
                                }
                              });
  }

  private static boolean isJupyterFile(@NotNull PsiElement element) {
    if (!(element instanceof PsiFile)) {
      return false;
    }
    final VirtualFile virtualFile = ((PsiFile)element).getVirtualFile();
    return virtualFile != null &&
           Objects.equals(virtualFile.getExtension(), "ipynb");
  }

  private static void showSingletonNotification(@NotNull Project project,
                                                @NotNull @NotificationTitle String title,
                                                @NotNull @NotificationContent String htmlContent,
                                                @NotNull NotificationType type,
                                                @NotNull NotificationListener listener) {
    getSettings(project).shown = true;
    BALLOON_NOTIFICATIONS.createNotification(title, htmlContent, type, (notification, event) -> {
      try {
        listener.hyperlinkUpdate(notification, event);
      }
      finally {
        notification.expire();
      }
    }).notify(project);
  }

  @NotNull
  private static PyCharmProfessionalAdvertiserSettings getSettings(@NotNull Project project) {
    return ServiceManager.getService(project, PyCharmProfessionalAdvertiserSettings.class);
  }
}
