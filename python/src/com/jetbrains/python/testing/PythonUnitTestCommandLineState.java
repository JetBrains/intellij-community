package com.jetbrains.python.testing;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Leonid Shalupov
 */
public class PythonUnitTestCommandLineState extends CommandLineState {
  private PythonUnitTestRunConfiguration myConfig;
  private final List<Filter> myFilters;
  private static final String PYTHONUNBUFFERED = "PYTHONUNBUFFERED";
  private static final String PYTHONPATH = "PYTHONPATH";
  private static final String UTRUNNER_PY = "pycharm/utrunner.py";

  public PythonUnitTestRunConfiguration getConfig() {
    return myConfig;
  }

  public PythonUnitTestCommandLineState(PythonUnitTestRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(env);
    myConfig = runConfiguration;
    myFilters = filters;
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    final ProcessHandler processHandler = startProcess();
    final ConsoleView console = createAndAttachConsole(getConfig().getProject(), processHandler);

    return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
  }

  protected OSProcessHandler startProcess() throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();

    final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  private GeneralCommandLine generateCommandLine() {
    GeneralCommandLine cmd = new GeneralCommandLine();

    final File helpersRoot = PythonHelpersLocator.getHelpersRoot();

    cmd.setExePath(myConfig.getInterpreterPath());
    if (!StringUtil.isEmptyOrSpaces(myConfig.getWorkingDirectory())) {
      cmd.setWorkDirectory(myConfig.getWorkingDirectory());
    }

    cmd.getParametersList().addParametersString(myConfig.getInterpreterOptions());
    cmd.addParameter(new File(helpersRoot, UTRUNNER_PY).getAbsolutePath());
    for (String testSpec : getTestSpecs()) {
      cmd.addParameter(testSpec);
    }

    Map<String, String> envs = myConfig.getEnvs();
    if (envs == null)
      envs = new HashMap<String, String>();
    else
      envs = new HashMap<String, String>(envs);

    envs.put(PYTHONUNBUFFERED, "1");
    insertToPythonPath(envs, helpersRoot);

    cmd.setEnvParams(envs);
    cmd.setPassParentEnvs(myConfig.isPassParentEnvs());

    return cmd;
  }

  private List<String> getTestSpecs() {
    List<String> specs = new ArrayList<String>();

    switch (myConfig.getTestType()) {
      case TEST_SCRIPT:
        specs.add(myConfig.getScriptName());
        break;
      case TEST_CLASS:
        specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName());
        break;
      case TEST_METHOD:
        specs.add(myConfig.getScriptName() + "::" + myConfig.getClassName() + "::" + myConfig.getMethodName());
        break;
      case TEST_FOLDER:
        specs.add(myConfig.getFolderName() + "/");
        break;
      default:
        throw new IllegalArgumentException("Unknown test type: " + myConfig.getTestType());
    }

    return specs;
  }

  private static void insertToPythonPath(Map<String, String> envs, File path) {
    if (envs.containsKey(PYTHONPATH)) {
      envs.put(PYTHONPATH, path.getAbsolutePath() + ":" + envs.get(PYTHONPATH));
    } else {
      envs.put(PYTHONPATH, path.getAbsolutePath());
    }
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler) throws ExecutionException {
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.attachRunner(project, processHandler, this, getConfig(), "PythonUnitTestRunner.Splitter.Proportion");
    for (Filter filter : myFilters) {
      consoleView.addMessageFilter(filter);
    }
    return consoleView;
  }
}
