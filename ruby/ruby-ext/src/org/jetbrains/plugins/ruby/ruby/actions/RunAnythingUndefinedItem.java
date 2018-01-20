package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.run.ConsoleRunner;
import org.jetbrains.plugins.ruby.ruby.run.MergingCommandLineArgumentsProvider;
import org.jetbrains.plugins.ruby.ruby.run.RubyAbstractRunner;
import org.jetbrains.plugins.ruby.rvm.RVMSupportUtil;
import org.jetbrains.plugins.ruby.version.management.rbenv.gemsets.RbenvGemsetManager;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class RunAnythingUndefinedItem extends RunAnythingItem {
  static final Icon UNDEFINED_COMMAND_ICON = RubyIcons.RunAnything.Run_anything;
  private static final String TITLE = "Command execution";
  private static final ArrayList<String> COMMANDS = ContainerUtil.newArrayList(
    "bundle", "rake", "erb", "gem", "irb", "rdoc", "ruby");
  @Nullable private final Module myModule;
  @NotNull private final String myCommandLine;
  @NotNull private final Project myProject;

  public RunAnythingUndefinedItem(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    myProject = project;
    myModule = module;
    myCommandLine = commandLine;
  }

  @Override
  public void run(@NotNull Executor executor, @Nullable VirtualFile workDirectory) {
    Sdk sdk = RModuleUtil.getInstance().findRubySdkForModule(myModule);

    String command = myCommandLine;
    Map<String, String> env = ContainerUtil.newHashMap();
    if (RVMSupportUtil.isRVMInterpreter(sdk)) {
      command = getRVMAwareCommand(sdk);
    }
    else if (RbenvGemsetManager.isRbenvSdk(sdk)) {
      command = getRbenvAwareCommand(sdk, env);
    }

    final MergingCommandLineArgumentsProvider argumentsProvider =
      new MergingCommandLineArgumentsProvider(command.split(" "),
                                              null, null, Boolean.TRUE, env, sdk);

    String workingDir = RunAnythingItem.getActualWorkDirectory(myProject, workDirectory);
    ConsoleRunner.run(myProject, myModule, null, null, null, null,
                      ConsoleRunner.ProcessLaunchMode.BACKGROUND_TASK_WITH_PROGRESS, TITLE, workingDir,
                      argumentsProvider, null, false);
  }

  private String getRbenvAwareCommand(@NotNull Sdk sdk, @NotNull Map<String, String> env) {
    String exeCommand = myCommandLine.contains(" ") ? StringUtil.substringBefore(myCommandLine, " ") : myCommandLine;
    String shimsExec = RbenvGemsetManager.getShimsCommandPath(Objects.requireNonNull(exeCommand));
    if (shimsExec == null) return myCommandLine;

    for (String shim : Objects.requireNonNull(new File(shimsExec).getParentFile().list())) {
      if (shim.equals(exeCommand)) {
        shimsExec = RbenvGemsetManager.getShimsCommandPath(shim);
        break;
      }
    }

    if (shimsExec == null || !RunAnythingCache.getInstance(myProject).CAN_RUN_RBENV) return myCommandLine;

    RubyAbstractRunner.patchRbenvEnv(env, myModule, sdk);

    return shimsExec + (myCommandLine.contains(" ") ? " " + StringUtil.substringAfter(myCommandLine, " ") : "");
  }

  @NotNull
  private String getRVMAwareCommand(@NotNull Sdk sdk) {
    String exeCommand = (myCommandLine.contains(" ")) ? StringUtil.substringBefore(myCommandLine, " ") : myCommandLine;
    String version = RVMSupportUtil.getRVMSdkVersion(sdk);

    if (!COMMANDS.contains(exeCommand) || !RunAnythingCache.getInstance(myProject).CAN_RUN_RVM || version == null) return myCommandLine;

    return "rvm " + version + " do " + myCommandLine;
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

  @Override
  public String getText() {
    return myCommandLine;
  }

  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }
}
