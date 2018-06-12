package org.jetbrains.plugins.ruby.ruby.actions.commands;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.gem.GemsDataKeys;
import org.jetbrains.plugins.ruby.remote.RubyRemoteInterpreterManager;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.run.RubyAbstractRunner;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;
import org.jetbrains.plugins.ruby.utils.OSUtil;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.intellij.ide.actions.runAnything.items.RunAnythingCommandItem.getShellCommand;

public class RubyRunAnythingCommandCustomizer extends RunAnythingCommandCustomizer {
  private static final Logger LOG = Logger.getInstance(RubyRunAnythingCommandCustomizer.class);

  @NotNull
  @Override
  public GeneralCommandLine customizeCommandLine(@NotNull VirtualFile workDirectory,
                                                 @NotNull DataContext dataContext,
                                                 @NotNull GeneralCommandLine commandLine) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Module module = LangDataKeys.MODULE.getData(dataContext);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);

    String command = commandLine.getExePath();
    LOG.assertTrue(project != null);

    Map<String, String> env = ContainerUtil.newHashMap(commandLine.getEffectiveEnvironment());
    if (sdk != null && !RubyRemoteInterpreterManager.getInstance().isRemoteSdk(sdk)) {
      if (RVMSupportUtil.isRVMInterpreter(sdk)) {
        command = getRVMAwareCommand(sdk, command, project);
      }
      else if (RbenvGemsetManager.isRbenvSdk(sdk)) {
        command = getRbenvAwareCommand(sdk, env, command, project, module);
      }
      else {
        command = getRubyAwareCommand(sdk, env, command);
      }
    }

    List<String> shellCommand = ContainerUtil.newArrayList(getShellCommand());
    if (shellCommand.isEmpty()) {
      shellCommand = ParametersListUtil.parse(command, false, true);
    }
    else {
      shellCommand.add(command);
    }

    commandLine = new GeneralCommandLine(shellCommand)
      .withEnvironment(env)
      .withWorkDirectory(workDirectory.getPath());

    return commandLine;
  }

  @NotNull
  @Override
  public DataContext customizeDataContext(@NotNull DataContext dataContext) {
    final Module module = LangDataKeys.MODULE.getData(dataContext);

    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(module);
    HashMap<String, Object> dataMap = new HashMap<>();

    if (sdk != null) {
      dataMap.put(GemsDataKeys.SDK.getName(), sdk);
    }

    return SimpleDataContext.getSimpleContext(dataMap, dataContext);
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
    if (shimsExec == null || !RunAnythingCache.getInstance(project).CAN_RUN_RBENV) return commandLine;

    RubyAbstractRunner.patchRbenvEnv(env, module, sdk);

    return shimsExec + (commandLine.contains(" ") ? " " + StringUtil.substringAfter(commandLine, " ") : "");
  }

  @NotNull
  private static String getRVMAwareCommand(@NotNull Sdk sdk, @NotNull String commandLine, @NotNull Project project) {
    if (commandLine.startsWith("rvm ")) return commandLine;

    String version = RVMSupportUtil.getRVMSdkVersion(sdk);
    String gemset = RVMSupportUtil.getGemset(sdk);

    if (version == null) return commandLine;
    if (gemset != null) version += '@' + gemset;

    if (!RunAnythingCache.getInstance(project).CAN_RUN_RVM) return commandLine;

    return "rvm " + version + " do " + commandLine;
  }
}
