// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableWithId;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.*;
import org.jetbrains.plugins.terminal.ui.OpenPredefinedTerminalActionProvider;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class TerminalNewPredefinedSessionAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    RelativePoint popupPoint = getPreferredPopupPoint(e);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<OpenShellAction> shells = detectShells();
      List<AnAction> customActions = OpenPredefinedTerminalActionProvider.collectAll(project);
      ApplicationManager.getApplication().invokeLater(() -> {
        ListPopup popup = createPopup(shells, customActions, e.getDataContext());
        if (popupPoint != null) {
          popup.show(popupPoint);
        }
        else {
          popup.showInFocusCenter();
        }
        InputEvent inputEvent = e.getInputEvent();
        if (inputEvent != null && inputEvent.getComponent() != null) {
          PopupUtil.setPopupToggleComponent(popup, inputEvent.getComponent());
        }
      }, project.getDisposed());
    });
  }

  private static @Nullable RelativePoint getPreferredPopupPoint(@NotNull AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      Component comp = inputEvent.getComponent();
      if (comp instanceof AnActionHolder) {
        return new RelativePoint(comp.getParent(), new Point(comp.getX() + JBUI.scale(3), comp.getY() + comp.getHeight() + JBUI.scale(3)));
      }
    }
    return null;
  }

  private static @NotNull ListPopup createPopup(@NotNull List<OpenShellAction> shells,
                                                @NotNull List<AnAction> customActions,
                                                @NotNull DataContext dataContext) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(shells);
    group.addAll(customActions);
    if (shells.size() + customActions.size() > 0) {
      group.addSeparator();
    }
    group.add(new TerminalSettingsAction());
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext,
                                                               false, true, false, null, -1, null);
  }

  private static @NotNull List<OpenShellAction> detectShells() {
    return TerminalShellsDetector.detectShells()
      .stream()
      .collect(Collectors.groupingBy(DetectedShellInfo::getName, LinkedHashMap::new, Collectors.toList()))
      .values()
      .stream()
      .flatMap(shellInfos -> {
        if (shellInfos.size() > 1) {
          return shellInfos.stream().map(info -> {
            return createOpenShellAction(info.getPath(), info.getOptions(), info.getName() + " (" + info.getPath() + ")");
          });
        }
        else {
          DetectedShellInfo info = shellInfos.get(0);
          return Stream.of(createOpenShellAction(info.getPath(), info.getOptions(), info.getName()));
        }
      })
      .toList();
  }

  private static @NotNull OpenShellAction createOpenShellAction(@NotNull String shellPath,
                                                                @NotNull List<String> shellOptions,
                                                                @NlsSafe String presentableName) {
    List<String> shellCommand = ContainerUtil.concat(List.of(shellPath), shellOptions);
    Icon icon = shellPath.endsWith("wsl.exe") ? AllIcons.RunConfigurations.Wsl : null;
    return new OpenShellAction(presentableName, shellCommand, icon);
  }

  private static final class TerminalSettingsAction extends DumbAwareAction {

    private TerminalSettingsAction() {
      super(IdeBundle.message("action.text.settings"), null, AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        // Match the Terminal configurable by ID.
        // Can't use matching by configurable class name because actual configurable can be wrapped in case of Remote Dev.
        ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable -> {
          return configurable instanceof ConfigurableWithId withId &&
                 TerminalOptionsConfigurableKt.TERMINAL_CONFIGURABLE_ID.equals(withId.getId());
        }, null);
      }
    }
  }

  private static final class OpenShellAction extends DumbAwareAction {

    private final List<String> myCommand;

    private OpenShellAction(@NotNull @NlsActions.ActionText String presentableName, @NotNull List<String> command, @Nullable Icon icon) {
      super(presentableName, null, icon);
      myCommand = command;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        TerminalTabState tabState = new TerminalTabState();
        tabState.myTabName = getTemplatePresentation().getText();
        tabState.myShellCommand = myCommand;
        TerminalToolWindowManager.getInstance(project).createNewSession(tabState);
      }
    }
  }
}
