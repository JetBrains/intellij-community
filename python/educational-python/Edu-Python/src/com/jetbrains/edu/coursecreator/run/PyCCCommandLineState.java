package com.jetbrains.edu.coursecreator.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.python.run.CommandLinePatcher;
import com.jetbrains.python.run.PythonCommandLineState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PyCCCommandLineState extends PythonCommandLineState {
  private final PyCCRunTestConfiguration myRunConfiguration;
  private final VirtualFile myTaskDir;
  private final Task myTask;

  public PyCCCommandLineState(PyCCRunTestConfiguration runConfiguration,
                              ExecutionEnvironment env) {
    super(runConfiguration, env);
    myRunConfiguration = runConfiguration;

    VirtualFile testsFile = LocalFileSystem.getInstance().findFileByPath(myRunConfiguration.getPathToTest());
    assert testsFile != null;
    myTaskDir = StudyUtils.getTaskDir(testsFile);
    assert myTaskDir != null;
    myTask = StudyUtils.getTask(myRunConfiguration.getProject(), myTaskDir);
    assert myTask != null;
  }

  @Override
  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
    commandLine.setWorkDirectory(myTaskDir.getPath());
    ParamsGroup group = commandLine.getParametersList().getParamsGroup(GROUP_SCRIPT);
    assert group != null;

    Project project = myRunConfiguration.getProject();
    Course course = StudyTaskManager.getInstance(project).getCourse();
    assert course != null;

    group.addParameter(myRunConfiguration.getPathToTest());

    String path = getCurrentTaskFilePath();
    if (path != null) {
      group.addParameter(path);
    }
  }

  @Nullable
  private String getCurrentTaskFilePath() {
    String textFile = null;
    for (Map.Entry<String, TaskFile> entry : myTask.getTaskFiles().entrySet()) {
      String path = getTaskFilePath(entry.getKey());
      if (!entry.getValue().getActivePlaceholders().isEmpty()) {
        return path;
      }
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      if (virtualFile == null) {
        continue;
      }
      if (TextEditorProvider.isTextFile(virtualFile)) {
        textFile = path;
      }
    }
    return textFile;
  }


  private String getTaskFilePath(String name) {
    String taskDirPath = FileUtil.toSystemDependentName(myTaskDir.getPath());
    return myTaskDir.findChild(EduNames.SRC) != null ?
           FileUtil.join(taskDirPath, EduNames.SRC, name) :
           FileUtil.join(taskDirPath, name);
  }

  @Override
  public ExecutionResult execute(Executor executor, PythonProcessStarter processStarter, CommandLinePatcher... patchers) throws ExecutionException {
    ApplicationManager.getApplication().runWriteAction(() -> StudyCheckUtils.flushWindows(myTask, myTaskDir));

    return super.execute(executor, processStarter, patchers);
  }

  @Override
  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    ProcessHandler handler = super.doCreateProcess(commandLine);
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        ApplicationManager.getApplication().invokeLater(() -> EduUtils.deleteWindowDescriptions(myTask, myTaskDir));
      }
    });
    return handler;
  }
}
