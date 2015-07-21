/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.edu;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.intention.IntentionActionBean;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistryImpl;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.TipAndTrickBean;
import com.intellij.notification.EventLog;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileChooser.impl.FileChooserUtil;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.keymap.impl.KeymapImpl;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.*;
import com.intellij.platform.DirectoryProjectConfigurator;
import com.intellij.platform.PlatformProjectViewOpener;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author traff
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class PyCharmEduInitialConfigurator {
  @NonNls private static final String DISPLAYED_PROPERTY = "PyCharmEDU.initialConfigurationShown";

  @NonNls private static final String CONFIGURED = "PyCharmEDU.InitialConfiguration";

  private static final Set<String> UNRELATED_TIPS = Sets.newHashSet("LiveTemplatesDjango.html", "TerminalOpen.html",
                                                                    "Terminal.html", "ConfiguringTerminal.html");

  public static class First {

    public First() {
      patchRootAreaExtensions();
    }
  }

  /**
   * @noinspection UnusedParameters
   */
  public PyCharmEduInitialConfigurator(MessageBus bus,
                                       CodeInsightSettings codeInsightSettings,
                                       final PropertiesComponent propertiesComponent,
                                       FileTypeManager fileTypeManager,
                                       final ProjectManagerEx projectManager,
                                       RecentProjectsManager recentProjectsManager) {
    if (!propertiesComponent.getBoolean(CONFIGURED, false)) {
      propertiesComponent.setValue(CONFIGURED, "true");
      propertiesComponent.setValue("toolwindow.stripes.buttons.info.shown", "true");
      UISettings uiSettings = UISettings.getInstance();
      uiSettings.HIDE_TOOL_STRIPES = false;
      uiSettings.SHOW_MEMORY_INDICATOR = false;
      uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = true;
      uiSettings.SHOW_MAIN_TOOLBAR = false;
      codeInsightSettings.REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;

      Registry.get("ide.new.settings.dialog").setValue(true);

      GeneralSettings.getInstance().setShowTipsOnStartup(false);

      EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
      EditorSettingsExternalizable.getInstance().getOptions().ARE_LINE_NUMBERS_SHOWN = true;
      final CodeStyleSettings settings = CodeStyleSettingsManager.getInstance().getCurrentSettings();
      settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
      settings.getCommonSettings(PythonLanguage.getInstance()).ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
      uiSettings.SHOW_DIRECTORY_FOR_NON_UNIQUE_FILENAMES = true;
      uiSettings.SHOW_MEMORY_INDICATOR = false;
      final String ignoredFilesList = fileTypeManager.getIgnoredFilesList();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              FileTypeManager.getInstance().setIgnoredFilesList(ignoredFilesList + ";*$py.class");
            }
          });
        }
      });
      PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP = false;
    }

    if (!propertiesComponent.isValueSet(DISPLAYED_PROPERTY)) {

      bus.connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
        @Override
        public void welcomeScreenDisplayed() {

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              if (!propertiesComponent.isValueSet(DISPLAYED_PROPERTY)) {
                GeneralSettings.getInstance().setShowTipsOnStartup(false);
                propertiesComponent.setValue(DISPLAYED_PROPERTY, "true");

                patchKeymap();
              }
            }
          });
        }
      });
    }

    bus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerAdapter() {
      @Override
      public void projectOpened(final Project project) {
        if (project.isDefault()) return;
        if (FileChooserUtil.getLastOpenedFile(project) == null) {
          FileChooserUtil.setLastOpenedFile(project, VfsUtil.getUserHomeDir());
        }

        patchProjectAreaExtensions(project);

        StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
          @Override
          public void run() {
            if (project.isDisposed()) return;

            ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
              int count = 0;

              public void run() {
                if (project.isDisposed()) return;
                if (count++ < 3) { // we need to call this after ToolWindowManagerImpl.registerToolWindowsFromBeans
                  ToolWindowManager.getInstance(project).invokeLater(this);
                  return;
                }
                ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Project");
                if (toolWindow.getType() != ToolWindowType.SLIDING) {
                  toolWindow.activate(null);
                }
              }
            });
          }
        });
      }
    });
  }

  private static void patchRootAreaExtensions() {
    ExtensionsArea rootArea = Extensions.getArea(null);

    for (ToolWindowEP ep : Extensions.getExtensions(ToolWindowEP.EP_NAME)) {
      if (ToolWindowId.FAVORITES_VIEW.equals(ep.id) || ToolWindowId.TODO_VIEW.equals(ep.id) || EventLog.LOG_TOOL_WINDOW_ID.equals(ep.id)
          || "Structure".equals(ep.id)) {
        rootArea.getExtensionPoint(ToolWindowEP.EP_NAME).unregisterExtension(ep);
      }
    }

    for (DirectoryProjectConfigurator ep : Extensions.getExtensions(DirectoryProjectConfigurator.EP_NAME)) {
      if (ep instanceof PlatformProjectViewOpener) {
        rootArea.getExtensionPoint(DirectoryProjectConfigurator.EP_NAME).unregisterExtension(ep);
      }
    }

    // unregister unrelated tips
    for (TipAndTrickBean tip : Extensions.getExtensions(TipAndTrickBean.EP_NAME)) {
      if (UNRELATED_TIPS.contains(tip.fileName)) {
        rootArea.getExtensionPoint(TipAndTrickBean.EP_NAME).unregisterExtension(tip);
      }
    }

    for (IntentionActionBean ep : Extensions.getExtensions(IntentionManager.EP_INTENTION_ACTIONS)) {
      if ("org.intellij.lang.regexp.intention.CheckRegExpIntentionAction".equals(ep.className)) {
        rootArea.getExtensionPoint(IntentionManager.EP_INTENTION_ACTIONS).unregisterExtension(ep);
      }
    }
  }

  private static void patchProjectAreaExtensions(@NotNull final Project project) {
    Executor debugExecutor = DefaultDebugExecutor.getDebugExecutorInstance();
    unregisterAction(debugExecutor.getId(), ExecutorRegistryImpl.RUNNERS_GROUP);
    unregisterAction(debugExecutor.getContextActionId(), ExecutorRegistryImpl.RUN_CONTEXT_GROUP);
    for (SelectInTarget target : Extensions.getExtensions(SelectInTarget.EP_NAME, project)) {
      if (ToolWindowId.FAVORITES_VIEW.equals(target.getToolWindowId())) {
        Extensions.getArea(project).getExtensionPoint(SelectInTarget.EP_NAME).unregisterExtension(target);
      }
    }
  }

  private static void unregisterAction(String actionId, String groupId) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(actionId);
    if (action != null) {
      ((DefaultActionGroup)actionManager.getAction(groupId)).remove(action);
      actionManager.unregisterAction(actionId);
    }
  }

  private static void patchKeymap() {
    Set<String> droppedActions = ContainerUtil.newHashSet(
      "AddToFavoritesPopup",
      "DatabaseView.ImportDataSources",
      "CompileDirty", "Compile",
      // hidden
      "AddNewFavoritesList", "EditFavorites", "RenameFavoritesList", "RemoveFavoritesList");
    KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();


    for (Keymap keymap : keymapManager.getAllKeymaps()) {
      if (keymap.canModify()) continue;

      KeymapImpl keymapImpl = (KeymapImpl)keymap;

      for (String id : keymapImpl.getOwnActionIds()) {
        if (droppedActions.contains(id)) keymapImpl.clearOwnActionsId(id);
      }
    }
  }

}
