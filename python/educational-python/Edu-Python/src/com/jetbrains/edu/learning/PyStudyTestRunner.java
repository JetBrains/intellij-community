package com.jetbrains.edu.learning;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.checker.StudyTestRunner;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

public class PyStudyTestRunner extends StudyTestRunner {
  private static final String PYTHONPATH = "PYTHONPATH";

  PyStudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    super(task, taskDir);
  }

  public Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException {
    final Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    Course course = myTask.getLesson().getCourse();
    PyEduPluginConfigurator configurator = new PyEduPluginConfigurator();
    String testsFileName = configurator.getTestFileName();
    if (myTask instanceof TaskWithSubtasks) {
      testsFileName = FileUtil.getNameWithoutExtension(testsFileName);
      int index = ((TaskWithSubtasks)myTask).getActiveSubtaskIndex();
      testsFileName += EduNames.SUBTASK_MARKER + index + "." + FileUtilRt.getExtension(configurator.getTestFileName());
    }
    final File testRunner = new File(myTaskDir.getPath(), testsFileName);
    final GeneralCommandLine commandLine = new GeneralCommandLine();
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
        File resourceFile = new File(course.getCourseDirectory());
        commandLine.addParameter(resourceFile.getPath());
        commandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
        return commandLine.createProcess();
      }
    }
    return null;
  }
}
