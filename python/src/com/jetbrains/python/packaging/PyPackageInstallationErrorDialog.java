// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.packaging;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.fields.ExpandableSupport;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.jetbrains.python.PySdkBundle;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.run.PyVirtualEnvReaderKt;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;

public class PyPackageInstallationErrorDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextArea myCommandOutput;
  private JPanel myCommandOutputPanel;
  private JBLabel packageName;
  private JBLabel interpreterPath;
  private JBLabel pythonVersion;
  private JBLabel myLabelWithLink;
  private JTextArea myCommandToTry;
  private JBScrollPane myScrollPaneForCommandToTry;

  public PyPackageInstallationErrorDialog(@NotNull @NlsContexts.DialogTitle String title,
                                          @NotNull PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription) {
    super(false);
    init();
    setTitle(title);
    final String output = errorDescription.getOutput();
    final String packageName = errorDescription.getPackageName();
    final String interpreterPath = errorDescription.getInterpreterPath();
    final String pythonVersion = errorDescription.getPythonVersion();
    final String command = errorDescription.getCommand();

    String activate = null;
    if (errorDescription.getSdk() != null) {
      final Pair<String, String> activateScript = PyVirtualEnvReaderKt.findActivateScript(interpreterPath, null);
      if (activateScript != null) {
        final String activateScriptPath = activateScript.component1();
        final String condaEnvFolder = activateScript.component2();
        if (condaEnvFolder != null) {
          activate = "conda activate " + condaEnvFolder;
        }
        else if (activateScriptPath != null) {
          if (SystemInfo.isWindows) {
            activate = activateScriptPath;
          }
          else {
            activate = "source " + activateScriptPath;
          }
        }
      }
    }

    final String commandToTry = activate != null ? activate + "\n" + command : command;
    myCommandToTry.setText(commandToTry);
    ExtendableTextComponent.Extension extension =
      ExtendableTextComponent.Extension.create(AllIcons.Actions.Copy, AllIcons.General.CopyHovered,
                                               ActionsBundle.message("action.EditorCopy.text"),
                                               () -> {
                                                 if (!StringUtil.isEmpty(commandToTry)) {
                                                   CopyPasteManager.getInstance().setContents(
                                                     new StringSelection(commandToTry));
                                                 }
                                               });
    final JLabel label = ExpandableSupport.createLabel(extension);
    myScrollPaneForCommandToTry.getVerticalScrollBar().add(JBScrollBar.LEADING, label);

    myCommandOutputPanel.setVisible(output != null);
    if (output != null) {
      myCommandOutput.setText(output);
    }

    this.myLabelWithLink.setCopyable(true);
    this.packageName.setText(packageName != null ? packageName : PySdkBundle.message("python.sdk.packaging.unknown.package.data"));
    this.packageName.setCopyable(true);
    this.interpreterPath.setText(
      interpreterPath != null ? interpreterPath : PySdkBundle.message("python.sdk.packaging.unknown.package.data"));
    this.interpreterPath.setCopyable(true);
    this.pythonVersion.setText(pythonVersion != null ? pythonVersion : PySdkBundle.message("python.sdk.packaging.unknown.package.data"));
    this.pythonVersion.setCopyable(true);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
