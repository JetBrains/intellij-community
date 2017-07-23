package com.jetbrains.edu.learning.checker;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.PyEduPluginConfigurator;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;

class PyStudyTestRunner {
  private static final String PYTHONPATH = "PYTHONPATH";
  @NotNull private final Task myTask;
  @NotNull private final VirtualFile myTaskDir;
  private GeneralCommandLine myCommandLine;

  PyStudyTestRunner(@NotNull final Task task, @NotNull final VirtualFile taskDir) {
    myTask = task;
    myTaskDir = taskDir;
  }

  Process createCheckProcess(@NotNull final Project project, @NotNull final String executablePath) throws ExecutionException {
    final Sdk sdk = PythonSdkType.findPythonSdk(ModuleManager.getInstance(project).getModules()[0]);
    PyEduPluginConfigurator configurator = new PyEduPluginConfigurator();
    String testsFileName = configurator.getTestFileName();
    if (myTask instanceof TaskWithSubtasks) {
      testsFileName = FileUtil.getNameWithoutExtension(testsFileName);
      int index = ((TaskWithSubtasks)myTask).getActiveSubtaskIndex();
      testsFileName += EduNames.SUBTASK_MARKER + index + "." + FileUtilRt.getExtension(configurator.getTestFileName());
    }
    final File testRunner = new File(myTaskDir.getPath(), testsFileName);
    myCommandLine = new GeneralCommandLine();
    myCommandLine.withWorkDirectory(myTaskDir.getPath());
    final Map<String, String> env = myCommandLine.getEnvironment();

    final VirtualFile courseDir = project.getBaseDir();
    if (courseDir != null) {
      env.put(PYTHONPATH, courseDir.getPath());
    }
    if (sdk != null) {
      String pythonPath = sdk.getHomePath();
      if (pythonPath != null) {
        myCommandLine.setExePath(pythonPath);
        myCommandLine.addParameter(testRunner.getPath());
        myCommandLine.addParameter(FileUtil.toSystemDependentName(executablePath));
        return myCommandLine.createProcess();
      }
    }
    return null;
  }

  GeneralCommandLine getCommandLine() {
    return myCommandLine;
  }
}
