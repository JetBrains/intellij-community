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
package com.jetbrains.python.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.DataManager;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.actions.PySciViewAction.ACTION_ID;

@State(name = "PySciProjectComponent", storages = @Storage("other.xml"))
public class PySciProjectComponent extends AbstractProjectComponent implements PersistentStateComponent<PySciProjectComponent.State> {
  private static final NotificationGroup BALLOON_NOTIFICATIONS = new NotificationGroup("Python Scientific View Advertiser",
                                                                                       NotificationDisplayType.STICKY_BALLOON,
                                                                                       false);
  private State myState = new State();

  protected PySciProjectComponent(Project project) {
    super(project);
  }

  public static PySciProjectComponent getInstance(Project project) {
    return project.getComponent(PySciProjectComponent.class);
  }

  public void useSciView(boolean useSciView) {
    myState.PY_SCI_VIEW = useSciView;
  }

  public boolean useSciView() {
    return myState.PY_SCI_VIEW;
  }

  @Override
  public void projectOpened() {
    if (myState.PY_SCI_VIEW) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
        final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(myProject.getBaseDir());
        if (directory != null) {
          DocumentationManager.getInstance(myProject).showJavaDocInfo(directory, directory);
        }
      });
    }
    else if (!myState.PY_SCI_VIEW_SUGGESTED) {
      StartupManager.getInstance(myProject).runWhenProjectIsInitialized(() -> {
        final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(myProject.getBaseDir());
        if (directory != null) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(directory);
          if (module != null) {
            final Sdk sdk = PythonSdkType.findPythonSdk(module);
            if (sdk != null) {
              final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
              final PyPackage numpy = PyPackageUtil.findPackage(packages, "numpy");

              if (numpy != null) {
                showInspectionAdvertisement(myProject);
              }
            }
          }
        }
      });
    }
  }

  private void showInspectionAdvertisement(@NotNull Project project) {
    final String msg = "Your source code imports the 'numpy' package." +
                       "<br/>Would you like to enable Scientific View?<br/>" +
                       "<a href=\"#yes\">Yes</a>&nbsp;&nbsp;<a href=\"#no\">No</a>";
    showSingletonNotification(project, msg, NotificationType.INFORMATION, (notification, event) -> {
      myState.PY_SCI_VIEW_SUGGESTED = true;
      final boolean enabled = "#yes".equals(event.getDescription());
      if (enabled) {
        final AnAction action = ActionManager.getInstance().getAction(ACTION_ID);
        if (action instanceof PySciViewAction) {
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
    BALLOON_NOTIFICATIONS.createNotification("Scientific View", htmlContent, type, (notification, event) -> {
      try {
        listener.hyperlinkUpdate(notification, event);
      }
      finally {
        notification.expire();
      }
    }).notify(project);
  }

  @Nullable
  @Override
  public PySciProjectComponent.State getState() {
    return myState;
  }

  @Override
  public void loadState(PySciProjectComponent.State state) {
    myState.PY_SCI_VIEW = state.PY_SCI_VIEW;
    myState.PY_SCI_VIEW_SUGGESTED = state.PY_SCI_VIEW_SUGGESTED;
  }

  public static class State {
    public boolean PY_SCI_VIEW = false;
    public boolean PY_SCI_VIEW_SUGGESTED = false;
  }
}
