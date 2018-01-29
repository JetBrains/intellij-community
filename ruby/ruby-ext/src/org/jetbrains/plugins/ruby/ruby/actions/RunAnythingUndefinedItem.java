package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.RBundle;
import org.jetbrains.plugins.ruby.remote.RubyRemoteInterpreterManager;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.run.RubyAbstractRunner;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;
import org.jetbrains.plugins.ruby.utils.OSUtil;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class RunAnythingUndefinedItem extends RunAnythingItem<String> {
  static final Icon UNDEFINED_COMMAND_ICON = RubyIcons.RunAnything.Run_anything;
  private static final ArrayList<String> COMMANDS = ContainerUtil.newArrayList(
    "bundle", "rake", "erb", "gem", "irb", "rdoc", "ruby", "rails");
  @Nullable private final Module myModule;
  @NotNull private final String myCommandLine;
  @NotNull private final Project myProject;

  public RunAnythingUndefinedItem(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    myProject = project;
    myModule = module;
    myCommandLine = commandLine;
  }

  @Override
  public void run(@NotNull Executor executor,
                  @Nullable VirtualFile workDirectory,
                  @Nullable Component component,
                  @NotNull Project project,
                  @Nullable AnActionEvent event) {
    runCommand(workDirectory, project, myCommandLine, myModule, executor);
  }

  public static void runCommand(@Nullable VirtualFile workDirectory,
                                @NotNull Project project,
                                @NotNull String commandString,
                                @Nullable Module module,
                                @NotNull Executor executor) {
    Collection<String> commands = RunAnythingCache.getInstance(project).getState().undefinedCommands;
    commands.remove(commandString);
    commands.add(commandString);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);

    GeneralCommandLine commandLine = new GeneralCommandLine().withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);

    String command = commandString;
    Map<String, String> env = ContainerUtil.newHashMap(commandLine.getEffectiveEnvironment());
    if (sdk != null && !RubyRemoteInterpreterManager.getInstance().isRemoteSdk(sdk)) {
      if (RVMSupportUtil.isRVMInterpreter(sdk)) {
        command = getRVMAwareCommand(sdk, commandString, project);
      }
      else if (RbenvGemsetManager.isRbenvSdk(sdk)) {
        command = getRbenvAwareCommand(sdk, env, commandString, project, module);
      }
      else {
        command = getRubyAwareCommand(sdk, env, commandString);
      }
    }

    List<String> shellCommand = getShellCommand();
    commandLine
      .withExePath(shellCommand.size() > 0 ? shellCommand.get(0) : ParametersListUtil.parse(command).get(0))
      .withParameters(shellCommand.size() == 2 ? ContainerUtil.immutableSingletonList(shellCommand.get(1)) : ContainerUtil.emptyList())
      .withParameters(command)
      .withEnvironment(env)
      .withWorkDirectory(RunAnythingItem.getActualWorkDirectory(project, workDirectory));

    runInConsole(commandLine, project);
  }

  private static void runInConsole(@NotNull GeneralCommandLine commandLine, @NotNull Project project) {
    try {
      KillableColoredProcessHandler processHandler = new KillableColoredProcessHandler(commandLine) {
        @Override
        protected void notifyProcessTerminated(int exitCode) {
          RunContentDescriptor contentDescriptor = ExecutionManager.getInstance(project).getContentManager()
                                                                   .findContentDescriptor(DefaultRunExecutor.getRunExecutorInstance(),
                                                                                          this);

          if (contentDescriptor != null && contentDescriptor.getExecutionConsole() instanceof ConsoleView) {
            ((ConsoleView)contentDescriptor.getExecutionConsole())
              .print(RBundle.message("run.anything.console.process.finished", exitCode), ConsoleViewContentType.SYSTEM_OUTPUT);
          }
          super.notifyProcessTerminated(exitCode);
        }
      };

      final RunContentExecutor contentExecutor = new RunContentExecutor(project, processHandler)
        .withTitle(RBundle.message("run.anything.console.title"))
        .withStop(processHandler::destroyProcess, () -> !processHandler.isProcessTerminated())
        .withActivateToolWindow(true);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (!project.isDisposed()) {
          contentExecutor.run();
        }
      });
    }
    catch (ExecutionException e) {
      Messages.showInfoMessage(project, e.getMessage(), RBundle.message("run.anything.console.error.title"));
    }
  }

  @NotNull
  private static List<String> getShellCommand() {
    if (SystemInfoRt.isWindows) return ContainerUtil.immutableList(ExecUtil.getWindowsShellName(), "/c");

    String shell = System.getenv("SHELL");
    return shell == null || !new File(shell).canExecute() ? ContainerUtil.emptyList() : ContainerUtil.immutableList(shell, "-c");
  }

  private static String getRubyAwareCommand(@NotNull Sdk sdk, @NotNull Map<String, String> env, @NotNull String commandLine) {
    VirtualFile sdkHomeDirectory = sdk.getHomeDirectory();
    if (sdkHomeDirectory == null) return commandLine;

    VirtualFile parent = sdkHomeDirectory.getParent();
    if (parent == null) return commandLine;

    final String path = FileUtil.toSystemDependentName(parent.getPath());
    final String envName = OSUtil.getPathEnvVariableName();
    final String newPath = OSUtil.prependToPathEnvVariable(env.get(envName), path);
    env.put(envName, newPath);

    return commandLine;
  }

  private static String getRbenvAwareCommand(@NotNull Sdk sdk,
                                             @NotNull Map<String, String> env,
                                             @NotNull String commandLine,
                                             @NotNull Project project,
                                             @Nullable Module module) {
    String exeCommand = commandLine.contains(" ") ? StringUtil.substringBefore(commandLine, " ") : commandLine;
    String shimsExec = RbenvGemsetManager.getShimsCommandPath(Objects.requireNonNull(exeCommand));
    if (shimsExec == null) return commandLine;

    for (String shim : Objects.requireNonNull(new File(shimsExec).getParentFile().list())) {
      if (shim.equals(exeCommand)) {
        shimsExec = RbenvGemsetManager.getShimsCommandPath(shim);
        break;
      }
    }

    if (shimsExec == null || !RunAnythingCache.getInstance(project).CAN_RUN_RBENV) return commandLine;

    RubyAbstractRunner.patchRbenvEnv(env, module, sdk);

    return shimsExec + (commandLine.contains(" ") ? " " + StringUtil.substringAfter(commandLine, " ") : "");
  }

  @NotNull
  private static String getRVMAwareCommand(@NotNull Sdk sdk, @NotNull String commandLine, @NotNull Project project) {
    if (commandLine.startsWith("rvm ")) return commandLine;

    String exeCommand = (commandLine.contains(" ")) ? StringUtil.substringBefore(commandLine, " ") : commandLine;
    String version = RVMSupportUtil.getRVMSdkVersion(sdk);
    String gemset = RVMSupportUtil.getGemset(sdk);

    if (version == null) return commandLine;
    if (gemset != null) version += '@' + gemset;

    if (!COMMANDS.contains(exeCommand) || !RunAnythingCache.getInstance(project).CAN_RUN_RVM) return commandLine;

    return "rvm " + version + " do " + commandLine;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingUndefinedItem item = (RunAnythingUndefinedItem)o;
    return Objects.equals(myModule, item.myModule) &&
           Objects.equals(myCommandLine, item.myCommandLine) &&
           Objects.equals(myProject, item.myProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myCommandLine, myProject);
  }

  @NotNull
  @Override
  public String getText() {
    return myCommandLine;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }

  @NotNull
  @Override
  public String getValue() {
    return myCommandLine;
  }

  @NotNull
  @Override
  public Component getComponent(boolean isSelected) {
    return RunAnythingUtil.getUndefinedCommandCellRendererComponent(this, isSelected);
  }
}
