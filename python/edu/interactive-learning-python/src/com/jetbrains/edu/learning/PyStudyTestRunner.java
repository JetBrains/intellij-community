package com.jetbrains.edu.learning;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.course.Course;
import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

public class PyStudyTestRunner extends StudyTestRunner {
  private static final String PYTHONPATH = "PYTHONPATH";

  PyStudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskfile) {
    super(task, taskfile);
  }
  public Process createCheckProcess(Project project, String executablePath) throws ExecutionException {
    Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    File testRunner = new File(myTaskDir.getPath(), myTask.getTestFile());
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.withWorkDirectory(myTaskDir.getPath());
    final Map<String, String> env = commandLine.getEnvironment();

    final VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      env.put(PYTHONPATH, courseDir.getPath());
    }
    if (sdk != null) {
      String pythonPath = sdk.getHomePath();
      if (pythonPath != null) {
        commandLine.setExePath(pythonPath);
        commandLine.addParameter(testRunner.getPath());
        final Course course = StudyTaskManager.getInstance(project).getCourse();
        assert course != null;
        File resourceFile = new File(course.getCourseDirectory());
        commandLine.addParameter(resourceFile.getPath());
        commandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
        return commandLine.createProcess();
      }
    }
    return null;
  }
}
