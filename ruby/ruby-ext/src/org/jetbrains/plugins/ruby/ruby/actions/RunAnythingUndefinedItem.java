package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.VirtualFile;
import icons.RubyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ruby.ruby.RModuleUtil;
import org.jetbrains.plugins.ruby.ruby.run.ConsoleRunner;
import org.jetbrains.plugins.ruby.ruby.run.MergingCommandLineArgumentsProvider;

import javax.swing.*;
import java.util.Objects;

public class RunAnythingUndefinedItem extends RunAnythingItem {
  static final Icon UNDEFINED_COMMAND_ICON = RubyIcons.RunAnything.Run_anything;
  private static final String TITLE = "Command execution";
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
    final MergingCommandLineArgumentsProvider argumentsProvider =
      new MergingCommandLineArgumentsProvider(myCommandLine.split(" "),
                                              null, null, Boolean.TRUE, null, sdk);

    String workingDir = RunAnythingItem.getActualWorkDirectory(myProject, workDirectory);
    ConsoleRunner.run(myProject, myModule, null, null, null, null,
                      ConsoleRunner.ProcessLaunchMode.BACKGROUND_TASK_WITH_PROGRESS, TITLE, workingDir,
                      argumentsProvider, null, false);
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
