package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.process.RunnerMediator;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {

  // command line has a number of fixed groups of parameters; patchers should only operate on them and not the raw list.

  public static final String GROUP_EXE_OPTIONS = "Exe Options";
  public static final String GROUP_DEBUGGER = "Debugger";
  public static final String GROUP_SCRIPT = "Script";
  private final AbstractPythonRunConfiguration myConfig;
  private final List<Filter> myFilters;

  public boolean isDebug() {
    return isDebug(getConfigurationSettings());
  }

  protected static boolean isDebug(ConfigurationPerRunnerSettings configurationSettings) {
    return PyDebugRunner.PY_DEBUG_RUNNER.equals(configurationSettings.getRunnerId());
  }

  public PythonCommandLineState(AbstractPythonRunConfiguration runConfiguration, ExecutionEnvironment env, List<Filter> filters) {
    super(env);
    myConfig = runConfiguration;
    myFilters = filters;
  }

  @Nullable
  public PythonSdkFlavor getSdkFlavor() {
    return PythonSdkFlavor.getFlavor(myConfig.getSdkHome());
  }

  @Override
  public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    return execute(executor, (CommandLinePatcher[])null);
  }

  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(patchers);
    final ConsoleView console = createAndAttachConsole(myConfig.getProject(), processHandler, executor);

    List<AnAction> actions = Lists.newArrayList(createActions(console, processHandler));

    return new DefaultExecutionResult(console, processHandler, actions.toArray(new AnAction[actions.size()]));
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final TextConsoleBuilder consoleBuilder = createConsoleBuilder(project);
    for (Filter filter : myFilters) {
      consoleBuilder.addFilter(filter);
    }

    final ConsoleView consoleView = consoleBuilder.getConsole();
    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  private TextConsoleBuilder createConsoleBuilder(Project project) {
    if (isDebug()) {
      return new PyDebugConsoleBuilder(project);
    }
    else {
      return TextConsoleBuilderFactory.getInstance().createBuilder(project);
    }
  }

  protected ProcessHandler startProcess() throws ExecutionException {
    return startProcess(null);
  }

  /**
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   *
   * @param patchers any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   * @throws ExecutionException
   */
  protected ProcessHandler startProcess(CommandLinePatcher... patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine(patchers);

    final ProcessHandler processHandler = doCreateProcess(commandLine);
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

  public GeneralCommandLine generateCommandLine(CommandLinePatcher[] patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine();
    if (patchers != null) {
      for (CommandLinePatcher patcher : patchers) {
        if (patcher != null) patcher.patchCommandLine(commandLine);
      }
    }
    return commandLine;
  }

  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return PythonProcessRunner.createProcess(commandLine);
  }

  public GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    setRunnerPath(commandLine);

    // define groups
    createStandardGroupsIn(commandLine);

    buildCommandLineParameters(commandLine);

    initEnvironment(commandLine);
    return commandLine;
  }

  /**
   * Creates a number of parameter groups in the command line:
   * GROUP_EXE_OPTIONS, GROUP_DEBUGGER, GROUP_SCRIPT.
   * These are necessary for command line patchers to work properly.
   *
   * @param commandLine
   */
  public static void createStandardGroupsIn(GeneralCommandLine commandLine) {
    ParametersList params = commandLine.getParametersList();
    params.addParamsGroup(GROUP_EXE_OPTIONS);
    params.addParamsGroup(GROUP_DEBUGGER);
    params.addParamsGroup(GROUP_SCRIPT);
  }

  protected void initEnvironment(GeneralCommandLine commandLine) {
    Map<String, String> envs = myConfig.getEnvs();
    if (envs == null) {
      envs = new HashMap<String, String>();
    }
    else {
      envs = new HashMap<String, String>(envs);
    }

    addPredefinedEnvironmentVariables(envs, myConfig.isPassParentEnvs());
    addCommonEnvironmentVariables(envs);

    commandLine.setEnvParams(envs);
    commandLine.setPassParentEnvs(myConfig.isPassParentEnvs());

    buildPythonPath(commandLine, myConfig.isPassParentEnvs());
  }

  protected static void addCommonEnvironmentVariables(Map<String, String> envs) {
    PythonEnvUtil.setPythonUnbuffered(envs);
  }

  protected void addPredefinedEnvironmentVariables(Map<String, String> envs, boolean passParentEnvs) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
    if (flavor != null) {
      flavor.addPredefinedEnvironmentVariables(envs);
    }
  }

  private void buildPythonPath(GeneralCommandLine commandLine, boolean passParentEnvs) {
    Sdk pythonSdk = PythonSdkType.findSdkByPath(myConfig.getSdkHome());
    if (pythonSdk != null) {
      List<String> pathList = Lists.newArrayList(getAddedPaths(pythonSdk));
      pathList.addAll(collectPythonPath());
      initPythonPath(commandLine, passParentEnvs, pathList, myConfig.getInterpreterPath());
    }
  }

  public static void initPythonPath(GeneralCommandLine commandLine,
                                    boolean passParentEnvs,
                                    List<String> pathList,
                                    final String interpreterPath) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(interpreterPath);
    if (flavor != null) {
      flavor.initPythonPath(commandLine, pathList);
    }
    else {
      PythonSdkFlavor.initPythonPath(commandLine.getEnvParams(), passParentEnvs, pathList);
    }
  }

  public static List<String> getAddedPaths(Sdk pythonSdk) {
    List<String> pathList = new ArrayList<String>();
    final SdkAdditionalData sdkAdditionalData = pythonSdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PythonSdkAdditionalData) {
      final Set<VirtualFile> addedPaths = ((PythonSdkAdditionalData)sdkAdditionalData).getAddedPaths();
      for (VirtualFile file : addedPaths) {
        if (file.getFileSystem() instanceof JarFileSystem) {
          VirtualFile realFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
          if (realFile != null) {
            pathList.add(FileUtil.toSystemDependentName(realFile.getPath()));
          }
        }
        else {
          pathList.add(FileUtil.toSystemDependentName(file.getPath()));
        }
      }
    }
    return pathList;
  }

  protected Collection<String> collectPythonPath() {
    final Module module = myConfig.getModule();
    return collectPythonPath(module);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module) {
    Collection<String> pythonPathList = Sets.newLinkedHashSet();
    pythonPathList.add(PythonHelpersLocator.getHelpersRoot().getPath());
    if (module != null) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      addRoots(pythonPathList, moduleRootManager.getContentRoots());
      addRoots(pythonPathList, moduleRootManager.getSourceRoots());

      final Facet[] facets = FacetManager.getInstance(module).getAllFacets();
      for (Facet facet : facets) {
        if (facet instanceof PythonPathContributingFacet) {
          List<String> more_paths = ((PythonPathContributingFacet)facet).getAdditionalPythonPath();
          if (more_paths != null) pythonPathList.addAll(more_paths);
        }
      }
    }
    return pythonPathList;
  }


  private static void addRoots(Collection<String> pythonPathList, VirtualFile[] roots) {
    for (VirtualFile root : roots) {
      pythonPathList.add(FileUtil.toSystemDependentName(root.getPath()));
    }
  }

  protected void setRunnerPath(GeneralCommandLine commandLine) throws ExecutionException {
    String interpreterPath = getInterpreterPath();
    commandLine.setExePath(FileUtil.toSystemDependentName(interpreterPath));
  }

  protected String getInterpreterPath() throws ExecutionException {
    String interpreterPath = myConfig.getInterpreterPath();
    if (interpreterPath == null) {
      throw new ExecutionException("Cannot find Python interpreter for this run configuration");
    }
    return interpreterPath;
  }

  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
  }
}
