// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal.action;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.execution.wsl.WslDistributionManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.EnvironmentUtil;
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
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class TerminalNewPredefinedSessionAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    RelativePoint popupPoint = getPreferredPopupPoint(e);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<OpenShellAction> shells = detectShells();
      List<OpenShellAction> wsl = listOpenWslShellActions();
      List<AnAction> customActions = OpenPredefinedTerminalActionProvider.collectAll(project);
      ApplicationManager.getApplication().invokeLater(() -> {
        ListPopup popup = createPopup(shells, wsl, customActions, e.getDataContext());
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
                                                @NotNull List<OpenShellAction> wsl,
                                                @NotNull List<AnAction> customActions,
                                                @NotNull DataContext dataContext) {
    DefaultActionGroup group = new DefaultActionGroup();
    group.addAll(shells);
    group.addAll(wsl);
    group.addAll(customActions);
    if (shells.size() + wsl.size() + customActions.size() > 0) {
      group.addSeparator();
    }
    group.add(new TerminalSettingsAction());
    return JBPopupFactory.getInstance().createActionGroupPopup(null, group, dataContext,
                                                               false, true, true, null, -1, null);
  }

  private static @NotNull List<OpenShellAction> listOpenWslShellActions() {
    if (WSLDistribution.findWslExe() == null) return List.of();
    List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
    return ContainerUtil.map(distributions, (d) -> {
      return new OpenShellAction(() -> d.getMsId(), List.of("wsl.exe", "-d", d.getMsId()), AllIcons.RunConfigurations.Wsl);
    });
  }

  private static @NotNull List<OpenShellAction> detectShells() {
    List<OpenShellAction> actions = new ArrayList<>();
    if (SystemInfo.isUnix) {
      ContainerUtil.addIfNotNull(actions, create("/bin/bash", List.of(), "bash"));
      if (Files.exists(Path.of("/usr/local/bin/zsh"))) {
        ContainerUtil.addIfNotNull(actions, create("/usr/local/bin/zsh", List.of(), "zsh"));
      }
      else {
        ContainerUtil.addIfNotNull(actions, create("/usr/bin/zsh", List.of(), "zsh"));
      }
      ContainerUtil.addIfNotNull(actions, create("/usr/bin/fish", List.of(), "fish"));
    }
    else if (SystemInfo.isWindows) {
      File powershell = PathEnvironmentVariableUtil.findInPath("powershell.exe");
      if (powershell != null && StringUtil.startsWithIgnoreCase(powershell.getAbsolutePath(), "C:\\Windows\\System32\\WindowsPowerShell\\")) {
        ContainerUtil.addIfNotNull(actions, create(powershell.getAbsolutePath(), List.of(), "Windows PowerShell"));
      }
      File cmd = PathEnvironmentVariableUtil.findInPath("cmd.exe");
      if (cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        ContainerUtil.addIfNotNull(actions, create(cmd.getAbsolutePath(), List.of(), "Command Prompt"));
      }
      File pwsh = PathEnvironmentVariableUtil.findInPath("pwsh.exe");
      if (pwsh != null && StringUtil.startsWithIgnoreCase(pwsh.getAbsolutePath(), "C:\\Program Files\\PowerShell\\")) {
        ContainerUtil.addIfNotNull(actions, create(pwsh.getAbsolutePath(), List.of(), "PowerShell"));
      }
      File gitBash = new File("C:\\Program Files\\Git\\bin\\bash.exe");
      if (gitBash.isFile()) {
        ContainerUtil.addIfNotNull(actions, create(gitBash.getAbsolutePath(), List.of(), "Git Bash"));
      }
      String cmderRoot = EnvironmentUtil.getValue("CMDER_ROOT");
      if (cmderRoot != null && cmd != null && StringUtil.startsWithIgnoreCase(cmd.getAbsolutePath(), "C:\\Windows\\System32\\")) {
        File cmderInitBat = new File(cmderRoot, "vendor\\init.bat");
        if (cmderInitBat.isFile()) {
          ContainerUtil.addIfNotNull(actions, create(cmd.getAbsolutePath(), List.of("/k", cmderInitBat.getAbsolutePath()), "Cmder"));
        }
      }
    }
    return actions;
  }

  private static @Nullable OpenShellAction create(@NotNull String shellPath, @NotNull List<String> shellOptions, @NlsSafe String presentableName) {
    if (Files.exists(Path.of(shellPath))) {
      List<String> shellCommand = LocalTerminalDirectRunner.convertShellPathToCommand(shellPath);
      List<String> otherOptions = shellOptions.stream().filter(opt -> !shellCommand.contains(opt)).toList();
      return new OpenShellAction(() -> presentableName, ContainerUtil.concat(shellCommand, otherOptions), null);
    }
    return null;
  }

  private static final class TerminalSettingsAction extends DumbAwareAction {

    private TerminalSettingsAction() {
      super(IdeBundle.message("action.text.settings"), null, AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, TerminalOptionsConfigurable.class);
      }
    }
  }

  private static final class OpenShellAction extends DumbAwareAction {

    private final List<String> myCommand;
    private final Supplier<@NlsActions.ActionText String> myPresentableName;

    private OpenShellAction(@NotNull Supplier<@NlsActions.ActionText String> presentableName, @NotNull List<String> command, @Nullable Icon icon) {
      super(presentableName, icon);
      myPresentableName = presentableName;
      myCommand = command;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project != null) {
        var runner = DefaultTerminalRunnerFactory.getInstance().createLocalRunner(project);
        TerminalTabState tabState = new TerminalTabState();
        tabState.myTabName = myPresentableName.get();
        tabState.myShellCommand = myCommand;
        TerminalToolWindowManager.getInstance(project).createNewSession(runner, tabState);
      }
    }
  }
}
