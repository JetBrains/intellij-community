// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.rest.sphinx;

import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.buildout.BuildoutFacet;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonProcessRunner;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.sdk.PythonEnvUtil.*;

/**
 * User : catherine
 * base command for the sphinx actions
 * asks for the "Sphinx documentation sources"
 */
public class SphinxBaseCommand {

  protected boolean setWorkDir(Module module) {
    final ReSTService service = ReSTService.getInstance(module);
    String workDir = service.getWorkdir();
    if (workDir.isEmpty()) {
      AskForWorkDir dialog = new AskForWorkDir(module.getProject());
      if (!dialog.showAndGet()) {
        return false;
      }
      service.setWorkdir(dialog.getInputFile());
    }
    return true;
  }

  public static class AskForWorkDir extends DialogWrapper {
    private TextFieldWithBrowseButton myInputFile;
    private JPanel myPanel;

    private AskForWorkDir(Project project) {
      super(project);

      setTitle("Set Sphinx Working Directory: ");
      init();
      VirtualFile baseDir =  project.getBaseDir();
      String path = baseDir != null? baseDir.getPath() : "";
      myInputFile.setText(path);
      myInputFile.setEditable(false);
      myInputFile.addBrowseFolderListener("Choose Sphinx Working Directory (Containing Makefile): ", null, project,
                                          FileChooserDescriptorFactory.createSingleFolderDescriptor());

      myPanel.setPreferredSize(new Dimension(600, 20));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }

    public String getInputFile() {
      return myInputFile.getText();
    }
  }

  public void execute(@NotNull final Module module) {
    final Project project = module.getProject();

    try {
      if (!setWorkDir(module)) return;
      final ProcessHandler process = createProcess(module);
      new RunContentExecutor(project, process)
        .withFilter(new PythonTracebackFilter(project))
        .withTitle("reStructuredText")
        .withRerun(() -> execute(module))
        .withAfterCompletion(getAfterTask(module))
        .run();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "ReStructuredText Error");
    }
  }

  @Nullable
  protected Runnable getAfterTask(final Module module) {
    return () -> {
      final ReSTService service = ReSTService.getInstance(module);
      LocalFileSystem.getInstance().refreshAndFindFileByPath(service.getWorkdir());
    };
  }

  private ProcessHandler createProcess(Module module) throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLine(module, Collections.emptyList());
    ProcessHandler handler = PythonProcessRunner.createProcess(commandLine, false);
    ProcessTerminatedListener.attach(handler);
    return handler;
  }

  protected GeneralCommandLine createCommandLine(Module module, List<String> params) throws ExecutionException {
    Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      throw new ExecutionException("No sdk specified");
    }

    ReSTService service = ReSTService.getInstance(module);

    String sdkHomePath = sdk.getHomePath();

    GeneralCommandLine cmd = new GeneralCommandLine();
    if (sdkHomePath != null) {
      final String runnerName = "sphinx-quickstart" + (SystemInfo.isWindows ? ".exe" : "");
      String executablePath = PythonSdkType.getExecutablePath(sdkHomePath, runnerName);
      if (executablePath != null) {
        cmd.setExePath(executablePath);
      }
      else {
        cmd = PythonHelper.LOAD_ENTRY_POINT.newCommandLine(sdkHomePath, Lists.newArrayList());
      }
    }

    cmd.setWorkDirectory(service.getWorkdir().isEmpty()? module.getProject().getBasePath(): service.getWorkdir());
    PythonCommandLineState.createStandardGroups(cmd);
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;

    if (params != null) {
      for (String p : params) {
        scriptParams.addParameter(p);
      }
    }

    final Map<String, String> env = cmd.getEnvironment();
    setPythonIOEncoding(env, "utf-8");
    setPythonUnbuffered(env);
    if (sdkHomePath != null) {
      resetHomePathChanges(sdkHomePath, env);
    }
    env.put("PYCHARM_EP_DIST", "Sphinx");
    env.put("PYCHARM_EP_NAME", "sphinx-quickstart");

    List<String> pathList = Lists.newArrayList(PythonCommandLineState.getAddedPaths(sdk));
    pathList.addAll(PythonCommandLineState.collectPythonPath(module));

    PythonCommandLineState.initPythonPath(cmd, true, pathList, sdkHomePath);

    PythonSdkType.patchCommandLineForVirtualenv(cmd, sdkHomePath, true);
    BuildoutFacet facet = BuildoutFacet.getInstance(module);
    if (facet != null) {
      facet.patchCommandLineForBuildout(cmd);
    }

    return cmd;
  }

}
