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
package com.jetbrains.pyqt;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

/**
 * @author yole
 */
public class CompileQrcAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    assert vFiles != null;
    Module module = e.getData(LangDataKeys.MODULE);
    String path = QtFileType.findQtTool(module, "pyrcc4");
    if (path == null) {
      path = QtFileType.findQtTool(module, "pyside-rcc");
    }
    if (path == null) {
      Messages.showErrorDialog(project, "Could not find pyrcc4 or pyside-rcc for selected Python interpreter", "Compile .qrc file");
      return;
    }
    CompileQrcDialog dialog = new CompileQrcDialog(project, vFiles);
    dialog.show();
    if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }

    GeneralCommandLine cmdLine = new GeneralCommandLine(path, "-o", dialog.getOutputPath());
    for (VirtualFile vFile : vFiles) {
      cmdLine.addParameter(vFile.getPath());
    }
    try {
      ProcessHandler process = new OSProcessHandler(cmdLine);
      ProcessTerminatedListener.attach(process);
      new RunContentExecutor(project, process)
        .withTitle("Compile .qrc")
        .run();
    }
    catch (ExecutionException ex) {
      Messages.showErrorDialog(project, "Error running " + path + ": " + ex.getMessage(), "Compile .qrc file");
    }
  }

  @Override
  public void update(AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE);
    VirtualFile[] vFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    e.getPresentation().setVisible(module != null && filesAreQrc(vFiles));
  }

  private static boolean filesAreQrc(VirtualFile[] vFiles) {
    if (vFiles == null || vFiles.length == 0) {
      return false;
    }
    for (VirtualFile vFile : vFiles) {
      if (!FileUtilRt.extensionEquals(vFile.getName(), "qrc")) {
        return false;
      }
    }
    return true;
  }

  public static class CompileQrcDialog extends DialogWrapper {
    private JPanel myPanel;
    private TextFieldWithBrowseButton myOutputFileField;

    protected CompileQrcDialog(Project project, VirtualFile[] vFiles) {
      super(project);
      if (vFiles.length == 1) {
        setTitle("Compile " + vFiles [0].getName());
      }
      else {
        setTitle("Compile " + vFiles.length + " .qrc files");
      }
      myOutputFileField.addBrowseFolderListener("Select output path:", null, project, FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
      init();
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }

    public String getOutputPath() {
      return myOutputFileField.getText();
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myOutputFileField;
    }
  }
}
