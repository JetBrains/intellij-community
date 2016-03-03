/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.run;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryImpl;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.remote.RemoteProcessControl;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.console.PyDebugConsoleBuilder;
import com.jetbrains.python.debugger.PyDebugRunner;
import com.jetbrains.python.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.library.PythonLibraryType;
import com.jetbrains.python.remote.PyRemotePathMapper;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {

  // command line has a number of fixed groups of parameters; patchers should only operate on them and not the raw list.

  public static final String GROUP_EXE_OPTIONS = "Exe Options";
  public static final String GROUP_DEBUGGER = "Debugger";
  public static final String GROUP_PROFILER = "Profiler";
  public static final String GROUP_COVERAGE = "Coverage";
  public static final String GROUP_SCRIPT = "Script";
  private final AbstractPythonRunConfiguration myConfig;

  private Boolean myMultiprocessDebug = null;
  private boolean myRunWithPty = PtyCommandLine.isEnabled();

  public boolean isDebug() {
    return PyDebugRunner.PY_DEBUG_RUNNER.equals(getEnvironment().getRunner().getRunnerId());
  }

  public static ServerSocket createServerSocket() throws ExecutionException {
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

  public PythonCommandLineState(AbstractPythonRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(env);
    myConfig = runConfiguration;
  }

  @Nullable
  public PythonSdkFlavor getSdkFlavor() {
    return PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
  }

  public Sdk getSdk() {
    return myConfig.getSdk();
  }

  @NotNull
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
    final ConsoleView consoleView = createConsoleBuilder(project).getConsole();
    consoleView.addMessageFilter(createUrlFilter(processHandler));

    addTracebackFilter(project, consoleView, processHandler);

    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected void addTracebackFilter(Project project, ConsoleView consoleView, ProcessHandler processHandler) {
    if (PySdkUtil.isRemote(myConfig.getSdk())) {
      assert processHandler instanceof RemoteProcessControl;
      consoleView
        .addMessageFilter(new PyRemoteTracebackFilter(project, myConfig.getWorkingDirectory(), (RemoteProcessControl)processHandler));
    }
    else {
      consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfig.getWorkingDirectorySafe()));
    }
    consoleView.addMessageFilter(createUrlFilter(processHandler)); // Url filter is always nice to have
  }

  private TextConsoleBuilder createConsoleBuilder(Project project) {
    if (isDebug()) {
      return new PyDebugConsoleBuilder(project, PythonSdkType.findSdkByPath(myConfig.getInterpreterPath()));
    }
    else {
      return TextConsoleBuilderFactory.getInstance().createBuilder(project);
    }
  }

  @Override
  @NotNull
  protected ProcessHandler startProcess() throws ExecutionException {
    return startProcess(new CommandLinePatcher[]{});
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
    PythonRunConfigurationExtensionsManager.getInstance()
      .patchCommandLine(myConfig, getRunnerSettings(), commandLine, getEnvironment().getRunner().getRunnerId());
    Sdk sdk = PythonSdkType.findSdkByPath(myConfig.getInterpreterPath());
    final ProcessHandler processHandler;
    if (PySdkUtil.isRemote(sdk)) {
      PyRemotePathMapper pathMapper = createRemotePathMapper();
      processHandler = createRemoteProcessStarter().startRemoteProcess(sdk, commandLine, myConfig.getProject(), pathMapper);
    }
    else {
      EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(commandLine);
      processHandler = doCreateProcess(commandLine);
      ProcessTerminatedListener.attach(processHandler);
    }

    // attach extensions
    PythonRunConfigurationExtensionsManager.getInstance().attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

    return processHandler;
  }

  @Nullable
  private PyRemotePathMapper createRemotePathMapper() {
    if (myConfig.getMappingSettings() == null) {
      return null;
    }
    else {
      return PyRemotePathMapper.fromSettings(myConfig.getMappingSettings(), PyRemotePathMapper.PyPathMappingType.USER_DEFINED);
    }
  }

  protected PyRemoteProcessStarter createRemoteProcessStarter() {
    return new PyRemoteProcessStarter();
  }


  public GeneralCommandLine generateCommandLine(CommandLinePatcher[] patchers) {
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

  public GeneralCommandLine generateCommandLine() {
    GeneralCommandLine commandLine = createPythonCommandLine(myConfig.getProject(), myConfig, isDebug(), myRunWithPty);

    buildCommandLineParameters(commandLine);

    customizeEnvironmentVars(commandLine.getEnvironment(), myConfig.isPassParentEnvs());

    return commandLine;
  }

  @NotNull
  public static GeneralCommandLine createPythonCommandLine(Project project, PythonRunParams config, boolean isDebug, boolean runWithPty) {
    GeneralCommandLine commandLine = generalCommandLine(runWithPty);

    commandLine.withCharset(EncodingProjectManager.getInstance(project).getDefaultCharset());

    createStandardGroups(commandLine);
    
    initEnvironment(project, commandLine, config, isDebug);

    setRunnerPath(project, commandLine, config);

    return commandLine;
  }

  private static GeneralCommandLine generalCommandLine(boolean runWithPty) {
    return runWithPty ? new PtyCommandLine() : new GeneralCommandLine();
  }

  /**
   * Creates a number of parameter groups in the command line:
   * GROUP_EXE_OPTIONS, GROUP_DEBUGGER, GROUP_SCRIPT.
   * These are necessary for command line patchers to work properly.
   *
   * @param commandLine
   */
  public static void createStandardGroups(GeneralCommandLine commandLine) {
    ParametersList params = commandLine.getParametersList();
    params.addParamsGroup(GROUP_EXE_OPTIONS);
    params.addParamsGroup(GROUP_DEBUGGER);
    params.addParamsGroup(GROUP_PROFILER);
    params.addParamsGroup(GROUP_COVERAGE);
    params.addParamsGroup(GROUP_SCRIPT);
  }

  protected static void initEnvironment(Project project, GeneralCommandLine commandLine, PythonRunParams myConfig, boolean isDebug) {
    Map<String, String> env = Maps.newHashMap();

    setupEncodingEnvs(env, commandLine.getCharset());

    if (myConfig.getEnvs() != null) {
      env.putAll(myConfig.getEnvs());
    }

    addCommonEnvironmentVariables(env);

    commandLine.getEnvironment().clear();
    commandLine.getEnvironment().putAll(env);
    commandLine.withParentEnvironmentType(myConfig.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);

    buildPythonPath(project, commandLine, myConfig, isDebug);
  }

  protected static void addCommonEnvironmentVariables(Map<String, String> env) {
    PythonEnvUtil.setPythonUnbuffered(env);
    env.put("PYCHARM_HOSTED", "1");
  }

  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
  }

  private static void setupEncodingEnvs(Map<String, String> envs, Charset charset) {
    PythonSdkFlavor.setupEncodingEnvs(envs, charset);
  }

  private static void buildPythonPath(Project project, GeneralCommandLine commandLine, PythonRunParams config, boolean isDebug) {
    Sdk pythonSdk = PythonSdkType.findSdkByPath(config.getSdkHome());
    if (pythonSdk != null) {
      List<String> pathList = Lists.newArrayList(getAddedPaths(pythonSdk));
      pathList.addAll(collectPythonPath(project, config, isDebug));
      initPythonPath(commandLine, config.isPassParentEnvs(), pathList, config.getSdkHome());
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
      PythonSdkFlavor.initPythonPath(commandLine.getEnvironment(), passParentEnvs, pathList);
    }
  }

  public static List<String> getAddedPaths(Sdk pythonSdk) {
    List<String> pathList = new ArrayList<String>();
    final SdkAdditionalData sdkAdditionalData = pythonSdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PythonSdkAdditionalData) {
      final Set<VirtualFile> addedPaths = ((PythonSdkAdditionalData)sdkAdditionalData).getAddedPathFiles();
      for (VirtualFile file : addedPaths) {
        addToPythonPath(file, pathList);
      }
    }
    return pathList;
  }

  private static void addToPythonPath(VirtualFile file, Collection<String> pathList) {
    if (file.getFileSystem() instanceof JarFileSystem) {
      final VirtualFile realFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
      if (realFile != null) {
        addIfNeeded(realFile, pathList);
      }
    }
    else {
      addIfNeeded(file, pathList);
    }
  }

  private static void addIfNeeded(@NotNull final VirtualFile file, @NotNull final Collection<String> pathList) {
    addIfNeeded(pathList, file.getPath());
  }

  protected static void addIfNeeded(Collection<String> pathList, String path) {
    final Set<String> vals = Sets.newHashSet(pathList);
    final String filePath = FileUtil.toSystemDependentName(path);
    if (!vals.contains(filePath)) {
      pathList.add(filePath);
    }
  }

  protected static Collection<String> collectPythonPath(Project project, PythonRunParams config, boolean isDebug) {
    final Module module = getModule(project, config);
    final HashSet<String> pythonPath =
      Sets.newHashSet(collectPythonPath(module, config.shouldAddContentRoots(), config.shouldAddSourceRoots()));

    if (isDebug && PythonSdkFlavor.getFlavor(config.getSdkHome()) instanceof JythonSdkFlavor) {
      //that fixes Jython problem changing sys.argv on execfile, see PY-8164
      pythonPath.add(PythonHelpersLocator.getHelperPath("pycharm"));
      pythonPath.add(PythonHelpersLocator.getHelperPath("pydev"));
    }

    return pythonPath;
  }

  @Nullable
  private static Module getModule(Project project, PythonRunParams config) {
    String name = config.getModuleName();
    return StringUtil.isEmpty(name) ? null : ModuleManager.getInstance(project).findModuleByName(name);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module) {
    return collectPythonPath(module, true, true);
  }

  @NotNull
  public static Collection<String> collectPythonPath(@Nullable Module module, boolean addContentRoots,
                                                     boolean addSourceRoots) {
    Collection<String> pythonPathList = Sets.newLinkedHashSet();
    if (module != null) {
      Set<Module> dependencies = new HashSet<Module>();
      ModuleUtilCore.getDependencies(module, dependencies);

      if (addContentRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getContentRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getContentRoots());
        }
      }
      if (addSourceRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getSourceRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getSourceRoots());
        }
      }

      addLibrariesFromModule(module, pythonPathList);
      addRootsFromModule(module, pythonPathList);
      for (Module dependency : dependencies) {
        addLibrariesFromModule(dependency, pythonPathList);
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
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          if (!PlatformUtils.isPyCharm()) {
            addToPythonPath(root, list);
          }
          else if (library instanceof LibraryImpl) {
            final PersistentLibraryKind<?> kind = ((LibraryImpl)library).getKind();
            if (kind == PythonLibraryType.getInstance().getKind()) {
              addToPythonPath(root, list);
            }
          }
        }
      }
    }
  }

  private static void addRootsFromModule(Module module, Collection<String> pythonPathList) {

    // for Jython
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

    //additional paths from facets (f.e. buildout)
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

  protected static void setRunnerPath(Project project, GeneralCommandLine commandLine, PythonRunParams config) {
    String interpreterPath = getInterpreterPath(project, config);
    if (StringUtil.isNotEmpty(interpreterPath)) {
      commandLine.setExePath(FileUtil.toSystemDependentName(interpreterPath));
    }
  }

  @Nullable
  public static String getInterpreterPath(Project project, PythonRunParams config) {
    String sdkHome = config.getSdkHome();
    if (config.isUseModuleSdk() || StringUtil.isEmpty(sdkHome)) {
      Module module = getModule(project, config);

      Sdk sdk = PythonSdkType.findPythonSdk(module);
      
      if (sdk != null) {
        sdkHome = sdk.getHomePath();
      }
    }

    return sdkHome;
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
    if (myMultiprocessDebug != null) {
      return myMultiprocessDebug;
    }
    else {
      return PyDebuggerOptionsProvider.getInstance(myConfig.getProject()).isAttachToSubprocess();
    }
  }

  public void setMultiprocessDebug(boolean multiprocessDebug) {
    myMultiprocessDebug = multiprocessDebug;
  }

  public void setRunWithPty(boolean runWithPty) {
    myRunWithPty = runWithPty;
  }

  @NotNull
  protected UrlFilter createUrlFilter(ProcessHandler handler) {
    return new UrlFilter();
  }
}
