package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase extends PythonCommandLineState {
  protected final AbstractPythonRunConfiguration myConfiguration;

  private static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";

  public PythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env, Collections.<Filter>emptyList());
    myConfiguration = configuration;
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler) throws ExecutionException {
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.attachRunner("PythonUnitTestRunner", processHandler, this, myConfiguration);
    consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfiguration.getWorkingDirectory()));
    return consoleView;
  }

  protected GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine cmd = new GeneralCommandLine();

    setRunnerPath(cmd);
    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory())) {
      cmd.setWorkDirectory(myConfiguration.getWorkingDirectory());
    }

    Map<String, String> envs = myConfiguration.getEnvs();
    if (envs == null)
      envs = new HashMap<String, String>();
    else
      envs = new HashMap<String, String>(envs);

    envs.put(PYTHONUNBUFFERED, "1");
    cmd.setEnvParams(envs);

    List<String> pythonPathList = buildPythonPath();
    String pythonPath = StringUtil.join(pythonPathList, File.pathSeparator);
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(myConfiguration.getInterpreterPath());
    if (flavor != null) {
      flavor.addToPythonPath(cmd, pythonPath);
    }

    cmd.getParametersList().addParametersString(myConfiguration.getInterpreterOptions());
    addTestRunnerParameters(cmd);

    cmd.setPassParentEnvs(myConfiguration.isPassParentEnvs());

    return cmd;
  }

  protected List<String> buildPythonPath() {
    List<String> pythonPathList = new ArrayList<String>();
    pythonPathList.add(PythonHelpersLocator.getHelpersRoot().getPath());
    final Module module = myConfiguration.getModule();
    if (module != null) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        pythonPathList.add(FileUtil.toSystemDependentName(contentRoot.getPath()));
      }
    }
    return pythonPathList;
  }

  protected abstract void addTestRunnerParameters(GeneralCommandLine cmd);
}
