package com.jetbrains.python.testing;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase extends CommandLineState {
  protected final AbstractPythonRunConfiguration myConfiguration;

  private static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
  private static final String PYTHONPATH = "PYTHONPATH";

  public PythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env) {
    super(env);
    myConfiguration = configuration;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView console = createAndAttachConsole(myConfiguration.getProject(), processHandler);

    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler) throws ExecutionException {
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.attachRunner(processHandler, this, myConfiguration, "PythonUnitTestRunner.Splitter.Proportion");
    consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfiguration.getWorkingDirectory()));
    return consoleView;
  }

  protected OSProcessHandler startProcess() throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();

    final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  protected GeneralCommandLine generateCommandLine() {
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

    List<String> pythonPathList = new ArrayList<String>();
    pythonPathList.add(PythonHelpersLocator.getHelpersRoot().getPath());
    final Module module = myConfiguration.getModule();
    if (module != null) {
      final VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        pythonPathList.add(FileUtil.toSystemDependentName(contentRoot.getPath()));
      }
    }
    String pythonPath = StringUtil.join(pythonPathList, File.pathSeparator);
    if (new File(myConfiguration.getInterpreterPath()).getName().toLowerCase().startsWith("jython")) {  // HACK rewrite with cleaner API
      cmd.getParametersList().add("-Dpython.path=" + pythonPath);
    }
    else {
      insertToPythonPath(envs, pythonPath);
    }

    cmd.getParametersList().addParametersString(myConfiguration.getInterpreterOptions());
    addTestRunnerParameters(cmd);

    cmd.setEnvParams(envs);
    cmd.setPassParentEnvs(myConfiguration.isPassParentEnvs());

    return cmd;
  }

  protected void setRunnerPath(GeneralCommandLine cmd) {
    cmd.setExePath(myConfiguration.getInterpreterPath());
  }

  protected abstract void addTestRunnerParameters(GeneralCommandLine cmd);

  private static void insertToPythonPath(Map<String, String> envs, String path) {
    if (envs.containsKey(PYTHONPATH)) {
      envs.put(PYTHONPATH, path + File.pathSeparatorChar + envs.get(PYTHONPATH));
    } else {
      envs.put(PYTHONPATH, path);
    }
  }

}
