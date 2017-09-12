/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.inspections;

import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.actions.PySciProjectComponent;
import com.jetbrains.python.actions.PySciViewAction;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import org.jetbrains.annotations.NotNull;

import static com.jetbrains.python.actions.PySciViewAction.ACTION_ID;

public class PySciViewAdvertiser implements Annotator {

  private static final NotificationGroup BALLOON_NOTIFICATIONS = new NotificationGroup("Python Scientific View Advertiser",
                                                                                       NotificationDisplayType.STICKY_BALLOON, false);
  private static final Key<Boolean> BALLOON_SHOWING = Key.create("showingSciViewAdvertiserBalloon");

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof PyFile) {
      final PyFile pyFile = (PyFile)element;
      final Project project = element.getProject();

      if (PySciProjectComponent.getInstance(project).sciViewSuggested()) {
        return;
      }
      final VirtualFile vFile = pyFile.getVirtualFile();
      if (vFile != null && FileIndexFacade.getInstance(project).isInLibraryClasses(vFile)) {
        return;
      }

      final Boolean showingFlag = project.getUserData(BALLOON_SHOWING);
      if (showingFlag != null && showingFlag.booleanValue()) {
        return;
      }

      if (containsNumpyImports(pyFile)) {
        showInspectionAdvertisement(project);
      }
    }
  }

  private static void showInspectionAdvertisement(@NotNull Project project) {
    final String msg = "Your source code imports the 'numpy' package." +
                       "<br/>Would you like to enable Scientific View?<br/>" +
                       "<a href=\"#yes\">Yes</a>&nbsp;&nbsp;<a href=\"#no\">No</a>";
    showSingletonNotification(project, msg, NotificationType.INFORMATION, (notification, event) -> {
      final boolean enabled = "#yes".equals(event.getDescription());
      PySciProjectComponent.getInstance(project).sciViewSuggested(true);
      if (enabled) {
        final AnAction action = ActionManager.getInstance().getAction(ACTION_ID);
        if (action instanceof PySciViewAction) {
          PySciProjectComponent.getInstance(project).sciViewSuggested();
          final AnActionEvent anActionEvent = AnActionEvent.createFromDataContext(
            ActionPlaces.UNKNOWN, null, DataManager.getInstance().getDataContextFromFocus().getResult());
          ((PySciViewAction)action).setSelected(anActionEvent, true);
        }
      }
    });
  }

  private static void showSingletonNotification(@NotNull Project project,
                                                @NotNull String htmlContent,
                                                @NotNull NotificationType type,
                                                @NotNull NotificationListener listener) {
    project.putUserData(BALLOON_SHOWING, true);
    BALLOON_NOTIFICATIONS.createNotification("Scientific View", htmlContent, type, (notification, event) -> {
      try {
        listener.hyperlinkUpdate(notification, event);
      }
      finally {
        notification.expire();
      }
    }).notify(project);
  }

  private static boolean containsNumpyImports(@NotNull PyFile file) {
    for (PyImportElement importTarget : file.getImportTargets()) {
      final QualifiedName qName = importTarget.getImportedQName();
      if (qName != null && qName.matches("numpy")) {
        return true;
      }
    }
    return false;
  }

}
