// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.restructuredtext.python;

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
import com.intellij.restructuredtext.RestBundle;
import com.intellij.ui.viewModel.extraction.ToolWindowContentExtractor;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonProcessRunner;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonEnvUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

  public static final class AskForWorkDir extends DialogWrapper {
    private TextFieldWithBrowseButton myInputFile;
    private JPanel myPanel;

    private AskForWorkDir(Project project) {
      super(project);

      setTitle(RestBundle.message("sphinx.set.working.directory.dialog.title"));
      init();
      VirtualFile baseDir = project.getBaseDir();
      String path = baseDir != null ? baseDir.getPath() : "";
      myInputFile.setText(path);
      myInputFile.setEditable(false);
      myInputFile.addBrowseFolderListener(RestBundle.message("sphinx.choose.working.directory.browse.folder.title"), null, project,
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
      process.putUserData(ToolWindowContentExtractor.SYNC_TAB_TO_REMOTE_CLIENTS, true);
      new RunContentExecutor(project, process)
        .withFilter(new PythonTracebackFilter(project))
        .withTitle("reStructuredText")
        .withRerun(() -> execute(module))
        .withAfterCompletion(getAfterTask(module))
        .run();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), RestBundle.message("sphinx.restructured.text.error"));
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
    Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk == null) {
      throw new ExecutionException(PythonRestBundle.message("python.rest.no.sdk.specified"));
    }

    ReSTService service = ReSTService.getInstance(module);

    String sdkHomePath = sdk.getHomePath();

    GeneralCommandLine cmd = new GeneralCommandLine();
    if (sdkHomePath != null) {
      final String runnerName = "sphinx-quickstart" + (SystemInfo.isWindows ? ".exe" : "");
      var executablePathNio = PythonSdkUtil.getExecutablePath(Path.of(sdkHomePath), runnerName);
      String executablePath = executablePathNio != null ? executablePathNio.toString() : null;
      if (executablePath != null) {
        cmd.setExePath(executablePath);
      }
      else {
        cmd = PythonHelper.LOAD_ENTRY_POINT.newCommandLine(sdkHomePath, new ArrayList<>());
      }
    }

    cmd.setWorkDirectory(service.getWorkdir().isEmpty() ? module.getProject().getBasePath() : service.getWorkdir());
    PythonCommandLineState.createStandardGroups(cmd);
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;

    if (params != null) {
      for (String p : params) {
        scriptParams.addParameter(p);
      }
    }

    final Map<String, String> env = cmd.getEnvironment();
    PythonEnvUtil.setPythonIOEncoding(env, "utf-8");
    PythonEnvUtil.setPythonUnbuffered(env);
    if (sdkHomePath != null) {
      PythonEnvUtil.resetHomePathChanges(sdkHomePath, env);
    }
    env.put("PYCHARM_EP_DIST", "Sphinx");
    env.put("PYCHARM_EP_NAME", "sphinx-quickstart");

    List<String> pathList = Lists.newArrayList(PythonCommandLineState.getAddedPaths(sdk));
    pathList.addAll(PythonCommandLineState.collectPythonPath(module));

    PythonCommandLineState.initPythonPath(cmd, true, pathList, sdkHomePath);

    PythonSdkType.patchCommandLineForVirtualenv(cmd, sdk);

    return cmd;
  }
}
