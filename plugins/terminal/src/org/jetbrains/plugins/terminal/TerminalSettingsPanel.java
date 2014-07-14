/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class TerminalSettingsPanel {
  private JPanel myWholePanel;
  private TextFieldWithBrowseButton myShellPathField;
  private JBCheckBox mySoundBellCheckBox;
  private JBCheckBox myCloseSessionCheckBox;
  private JBCheckBox myMouseReportCheckBox;
  private JTextField myTabNameTextField;
  private JBCheckBox myPasteOnMiddleButtonCheckBox;
  private JBCheckBox myCopyOnSelectionCheckBox;
  private JBCheckBox myOverrideIdeShortcuts;
  private TerminalOptionsProvider myOptionsProvider;

  public JComponent createPanel(@NotNull TerminalOptionsProvider provider) {
    myOptionsProvider = provider;

    FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false);

    myShellPathField.addBrowseFolderListener(
      "",
      "Shell Executable Path",
      null,
      fileChooserDescriptor,
      TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
      false
    );

    return myWholePanel;
  }

  public boolean isModified() {
    return !Comparing.equal(myShellPathField.getText(), myOptionsProvider.getShellPath())
           || !Comparing.equal(myTabNameTextField.getText(), myOptionsProvider.getTabName())
           || (myCloseSessionCheckBox.isSelected() != myOptionsProvider.closeSessionOnLogout())
           || (myMouseReportCheckBox.isSelected() != myOptionsProvider.enableMouseReporting())
           || (mySoundBellCheckBox.isSelected() != myOptionsProvider.audibleBell())
           || (myCopyOnSelectionCheckBox.isSelected() != myOptionsProvider.copyOnSelection())
           || (myPasteOnMiddleButtonCheckBox.isSelected() != myOptionsProvider.pasteOnMiddleMouseButton())
           || (myOverrideIdeShortcuts.isSelected() != myOptionsProvider.overrideIdeShortcuts())
      ;
  }

  public void apply() {
    myOptionsProvider.setShellPath(myShellPathField.getText());
    myOptionsProvider.setTabName(myTabNameTextField.getText());
    myOptionsProvider.setCloseSessionOnLogout(myCloseSessionCheckBox.isSelected());
    myOptionsProvider.setReportMouse(myMouseReportCheckBox.isSelected());
    myOptionsProvider.setSoundBell(mySoundBellCheckBox.isSelected());
    myOptionsProvider.setCopyOnSelection(myCopyOnSelectionCheckBox.isSelected());
    myOptionsProvider.setPasteOnMiddleMouseButton(myPasteOnMiddleButtonCheckBox.isSelected());
    myOptionsProvider.setOverrideIdeShortcuts(myOverrideIdeShortcuts.isSelected());
  }

  public void reset() {
    myShellPathField.setText(myOptionsProvider.getShellPath());
    myTabNameTextField.setText(myOptionsProvider.getTabName());
    myCloseSessionCheckBox.setSelected(myOptionsProvider.closeSessionOnLogout());
    myMouseReportCheckBox.setSelected(myOptionsProvider.enableMouseReporting());
    mySoundBellCheckBox.setSelected(myOptionsProvider.audibleBell());
    myCopyOnSelectionCheckBox.setSelected(myOptionsProvider.copyOnSelection());
    myPasteOnMiddleButtonCheckBox.setSelected(myOptionsProvider.pasteOnMiddleMouseButton());
    myOverrideIdeShortcuts.setSelected(myOptionsProvider.overrideIdeShortcuts());
  }
}
