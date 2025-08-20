// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.sh.backend.run;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.sh.psi.ShFile;
import com.intellij.sh.run.ShRunConfiguration;
import com.intellij.sh.run.ShConfigurationType;
import com.intellij.sh.run.ShRunnerAdditionalCondition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.eel.provider.EelProviderUtil.getEelDescriptor;
import static com.intellij.platform.eel.provider.utils.EelPathUtils.getNioPath;

final class ShRunFileAction extends DumbAwareAction {
  static final String ID = "runShellFileAction";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file == null) return;
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;

    Project project = file.getProject();
    ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext(), e.getPlace());
    ShRunConfigurationProducer configProducer = RunConfigurationProducer.getInstance(ShRunConfigurationProducer.class);
    RunnerAndConfigurationSettings configurationSettings = configProducer.findExistingConfiguration(context);
    ShRunConfiguration runConfiguration;
    if (configurationSettings == null) {
      configurationSettings = RunManager.getInstance(project).createConfiguration(file.getName(), ShConfigurationType.class);
      runConfiguration = (ShRunConfiguration)configurationSettings.getConfiguration();
      runConfiguration.setScriptPath(virtualFile.getPath());
      runConfiguration.setExecuteScriptFile(true);
      runConfiguration.setScriptWorkingDirectory(virtualFile.getParent().getPath());
      if (file instanceof ShFile) {
        @NlsSafe String defaultShell = ShConfigurationType.getDefaultShell(project);
        String shebang = ShShebangParserUtil.getShebangExecutable((ShFile)file);
        if (shebang != null) {
          final var eelDescriptor = getEelDescriptor(project);
          Pair<String, String> result = parseInterpreterAndOptions(shebang);
          runConfiguration.setInterpreterPath(getNioPath(result.first, eelDescriptor).toString());
          runConfiguration.setInterpreterOptions(result.second);
        } else {
          runConfiguration.setInterpreterPath(defaultShell);
        }
      }
      else {
        runConfiguration.setInterpreterPath("");
      }
    } else {
      runConfiguration = (ShRunConfiguration)configurationSettings.getConfiguration();
    }

    ExecutionEnvironmentBuilder builder =
      ExecutionEnvironmentBuilder.createOrNull(DefaultRunExecutor.getRunExecutorInstance(), runConfiguration);
    if (builder != null) {
      ExecutionManager.getInstance(project).restartRunProfile(builder.build());
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  static @NotNull Pair<String, String> parseInterpreterAndOptions(@NotNull String shebang) {
    String[] splitShebang = shebang.split(" ");
    if (splitShebang.length > 1) {
      String shebangParam = splitShebang[splitShebang.length - 1];
      if (!shebangParam.contains("/") && !shebangParam.contains("\\")) {
        return Pair.create(shebang.substring(0, shebang.length() - shebangParam.length() - 1), shebangParam);
      }
    }
    return Pair.create(shebang, "");
  }

  private static boolean isEnabled(@NotNull AnActionEvent e) {
    if (e.getProject() != null) {
      PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
      if (file != null) {
        var runningProhibited = ContainerUtil.exists(ShRunnerAdditionalCondition.EP.getExtensionsIfPointIsRegistered(),
                                                     runningCondition -> {
                                                       return runningCondition.isRunningProhibitedForFile(file);
                                                     });
        if (runningProhibited) return false;
        if (file instanceof ShFile) return true;
        PsiElement firstChild = file.findElementAt(0);
        return firstChild instanceof PsiComment && firstChild.getText().startsWith("#!");
      }
    }
    return false;
  }
}