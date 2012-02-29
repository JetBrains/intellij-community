package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.remote.PyRemoteDebugConfiguration;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.remote.PyRemoteInterpreterException;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.PythonRemoteSdkAdditionalData;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
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
  private boolean myMultiprocessDebug = false;

  public boolean isDebug() {
    return isDebug(getConfigurationSettings());
  }

  public Pair<ServerSocket, Integer> createDebugServerSocket() throws ExecutionException {
    ServerSocket serverSocket = createServerSocket();

    Sdk sdk = PythonSdkType.findSdkByPath(myConfig.getSdkHome());

    if (sdk != null && sdk.getSdkAdditionalData() instanceof PythonRemoteSdkAdditionalData) {
      //remote interpreter
      return Pair.create(serverSocket, -serverSocket.getLocalPort());
    }
    else {
      return Pair.create(serverSocket, serverSocket.getLocalPort());
    }
  }

  private ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }
    return serverSocket;
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
      return new PyDebugConsoleBuilder(project, PythonSdkType.findSdkByPath(myConfig.getSdkHome()));
    }
    else {
      return TextConsoleBuilderFactory.getInstance().createBuilder(project);
    }
  }

  @NotNull
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

    // Extend command line
    RunnerSettings runnerSettings = getRunnerSettings();
    PythonRunConfigurationExtensionsManager.getInstance()
      .patchCommandLine(myConfig, runnerSettings, commandLine, getConfigurationSettings().getRunnerId());

    Sdk sdk = PythonSdkType.findSdkByPath(myConfig.getSdkHome());

    if (sdk != null && sdk.getSdkAdditionalData() instanceof PythonRemoteSdkAdditionalData) {
      return startRemoteProcess(sdk, commandLine);
    }
    else {
      final ProcessHandler processHandler = doCreateProcess(commandLine);
      ProcessTerminatedListener.attach(processHandler);

      // attach extensions
      PythonRunConfigurationExtensionsManager.getInstance().attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

      return processHandler;
    }
  }

  private ProcessHandler startRemoteProcess(Sdk sdk, GeneralCommandLine commandLine) throws ExecutionException {
    PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
    if (manager != null) {

      ProcessHandler processHandler =
        null;

      while (true) {
        try {
          processHandler =
            manager.doCreateProcess(myConfig.getProject(), (PythonRemoteSdkAdditionalData)sdk.getSdkAdditionalData(), commandLine,
                                    ((PythonRunConfiguration)myConfig).getMappingSettings());
          break;
        }
        catch (PyRemoteInterpreterException e) {
          if (Messages.showYesNoDialog(e.getMessage() + "\nTry again?", "Can't Run Remote Interpreter", Messages.getErrorIcon()) ==
              Messages.NO) {
            throw new ExecutionException("Can't run remote python interpreter: " + e.getMessage());
          }
        }
      }
      ProcessTerminatedListener.attach(processHandler);
      return processHandler;
    }
    else {
      throw new PythonRemoteInterpreterManager.PyRemoteInterpreterExecutionException();
    }
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
    envs.put("PYCHARM_HOSTED", "1");
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
        addToPythonPath(file, pathList);
      }
    }
    return pathList;
  }

  private static void addToPythonPath(VirtualFile file, Collection<String> pathList) {
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

  protected Collection<String> collectPythonPath() {
    final Module module = myConfig.getModule();
    return collectPythonPath(module);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module) {
    Collection<String> pythonPathList = Sets.newLinkedHashSet();
    pythonPathList.add(PythonHelpersLocator.getHelpersRoot().getPath());
    if (module != null) {
      addLibrariesFromModule(module, pythonPathList);
      Set<Module> dependencies = new HashSet<Module>();
      ModuleUtil.getDependencies(module, dependencies);
      for (Module dependency : dependencies) {
        addLibrariesFromModule(module, pythonPathList);
        addRootsFromModule(dependency, pythonPathList);
      }
    }
    return pythonPathList;
  }

  private static void addLibrariesFromModule(Module module, Collection<String> list) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final String name = ((LibraryOrderEntry)entry).getLibraryName();
        if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
          // skip libraries from Python facet
          continue;
        }
        for (VirtualFile root : ((LibraryOrderEntry)entry).getRootFiles(OrderRootType.CLASSES)) {
          addToPythonPath(root, list);
        }
      }
    }
  }

  private static void addRootsFromModule(Module module, Collection<String> pythonPathList) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    addRoots(pythonPathList, moduleRootManager.getContentRoots());
    addRoots(pythonPathList, moduleRootManager.getSourceRoots());

    final CompilerModuleExtension extension = CompilerModuleExtension.getInstance(module);
    if (extension != null) {
      final VirtualFile path = extension.getCompilerOutputPath();
      if (path != null) {
        pythonPathList.add(path.getPath());
      }
      final VirtualFile pathForTests = extension.getCompilerOutputPathForTests();
      if (pathForTests != null) {
        pythonPathList.add(pathForTests.getPath());
      }
    }

    final Facet[] facets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : facets) {
      if (facet instanceof PythonPathContributingFacet) {
        List<String> more_paths = ((PythonPathContributingFacet)facet).getAdditionalPythonPath();
        if (more_paths != null) pythonPathList.addAll(more_paths);
      }
    }
  }

  private static void addRoots(Collection<String> pythonPathList, VirtualFile[] roots) {
    for (VirtualFile root : roots) {
      addToPythonPath(root, pythonPathList);
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

  public boolean isMultiprocessDebug() {
    return myMultiprocessDebug;
  }

  public void setMultiprocessDebug(boolean multiprocessDebug) {
    myMultiprocessDebug = multiprocessDebug;
  }
}
