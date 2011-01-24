package com.jetbrains.python.testing;

import com.google.common.collect.Sets;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.facet.PythonPathContributingFacet;
import com.jetbrains.python.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonTestCommandLineStateBase extends PythonCommandLineState {
  protected final AbstractPythonRunConfiguration myConfiguration;

  public PythonTestCommandLineStateBase(AbstractPythonRunConfiguration configuration, ExecutionEnvironment env) {
    super(configuration, env, Collections.<Filter>emptyList());
    myConfiguration = configuration;
  }

  @NotNull
  protected ConsoleView createAndAttachConsole(Project project, ProcessHandler processHandler, Executor executor)
    throws ExecutionException {
    final ConsoleView consoleView = SMTestRunnerConnectionUtil.attachRunner("PythonUnitTestRunner", processHandler, this, myConfiguration,
                                                                            executor);
    consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfiguration.getWorkingDirectory()));
    return consoleView;
  }

  public GeneralCommandLine generateCommandLine() throws ExecutionException {
    GeneralCommandLine cmd = super.generateCommandLine();

    if (!StringUtil.isEmptyOrSpaces(myConfiguration.getWorkingDirectory())) {
      cmd.setWorkDirectory(myConfiguration.getWorkingDirectory());
    }

    ParamsGroup exe_options = cmd.getParametersList().getParamsGroup(GROUP_EXE_OPTIONS);
    assert exe_options != null;
    exe_options.addParametersString(myConfiguration.getInterpreterOptions());
    addTestRunnerParameters(cmd);

    return cmd;
  }

  @Override
  protected void addPredefinedEnvironmentVariables(Map<String, String> envs, boolean passParentEnvs) {
    super.addPredefinedEnvironmentVariables(envs, passParentEnvs);
    Collection<String> pythonPathList = buildPythonPath();
    String pythonPath = StringUtil.join(pythonPathList, File.pathSeparator);

    if (passParentEnvs && !envs.containsKey(PythonSdkFlavor.PYTHONPATH)) {
      pythonPath = PythonSdkFlavor.appendSystemPythonPath(pythonPath);
    }

    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(myConfiguration.getInterpreterPath());
    if (flavor != null) {
      flavor.addToPythonPath(envs, pythonPath);
    }
  }

  protected Collection<String> buildPythonPath() {
    Collection<String> pythonPathList = Sets.newLinkedHashSet();
    pythonPathList.add(PythonHelpersLocator.getHelpersRoot().getPath());
    final Module module = myConfiguration.getModule();
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

  protected abstract void addTestRunnerParameters(GeneralCommandLine cmd);
  protected abstract List<String> getTestSpecs();
}
