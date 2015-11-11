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

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

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
      myInputFile.addBrowseFolderListener("Choose sphinx working directory (containing makefile): ", null, project,
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
        .withRerun(new Runnable() {
          @Override
          public void run() {
            execute(module);
          }
        })
        .withAfterCompletion(getAfterTask(module))
        .run();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "ReStructuredText Error");
    }
  }

  @Nullable
  protected Runnable getAfterTask(final Module module) {
    return new Runnable() {
      public void run() {
        final ReSTService service = ReSTService.getInstance(module);
        LocalFileSystem.getInstance().refreshAndFindFileByPath(service.getWorkdir());
      }
    };
  }

  private ProcessHandler createProcess(Module module) throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLine(module, Collections.<String>emptyList());
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
        cmd = PythonHelper.LOAD_ENTRY_POINT.newCommandLine(sdkHomePath, Lists.<String>newArrayList());
      }
    }

    cmd.setWorkDirectory(service.getWorkdir().isEmpty()? module.getProject().getBaseDir().getPath(): service.getWorkdir());
    PythonCommandLineState.createStandardGroups(cmd);
    ParamsGroup scriptParams = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert scriptParams != null;

    if (params != null) {
      for (String p : params) {
        scriptParams.addParameter(p);
      }
    }

    setPythonIOEncoding(cmd.getEnvironment(), "utf-8");
    setPythonUnbuffered(cmd.getEnvironment());
    cmd.getEnvironment().put("PYCHARM_EP_DIST", "Sphinx");
    cmd.getEnvironment().put("PYCHARM_EP_NAME", "sphinx-quickstart");

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
