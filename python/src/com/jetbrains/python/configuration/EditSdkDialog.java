// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.ClickListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.NullableFunction;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.MouseEvent;

/**
 * @author yole
 */
public class EditSdkDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myNameTextField;
  private TextFieldWithBrowseButton myInterpreterPathTextField;
  private JBCheckBox myAssociateCheckbox;
  private JBLabel myRemoveAssociationLabel;
  private final boolean myWasAssociated;
  private boolean myAssociationRemoved = false;

  protected EditSdkDialog(Project project, SdkModificator sdk, final NullableFunction<? super String, String> nameValidator) {
    super(project, true);
    setTitle(PyBundle.message("sdk.edit.dialog.title"));
    myNameTextField.setText(sdk.getName());
    myNameTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        @NlsSafe String nameError = nameValidator.fun(getName());
        setErrorText(nameError, myNameTextField);
        setOKActionEnabled(nameError == null);
      }
    });
    final String homePath = sdk.getHomePath();
    myInterpreterPathTextField.setText(homePath);
    myInterpreterPathTextField.addBrowseFolderListener(PyBundle.message("sdk.edit.dialog.specify.interpreter.path"), null, project,
                                                       PythonSdkType.getInstance().getHomeChooserDescriptor());
    myRemoveAssociationLabel.setVisible(false);
    final PythonSdkFlavor sdkFlavor = PythonSdkFlavor.getPlatformIndependentFlavor(homePath);
    if ((sdkFlavor instanceof VirtualEnvSdkFlavor) || (sdkFlavor instanceof CondaEnvSdkFlavor)) {
      PythonSdkAdditionalData data = (PythonSdkAdditionalData) sdk.getSdkAdditionalData();
      if (data != null) {
        final String path = data.getAssociatedModulePath();
        if (path != null) {
          myAssociateCheckbox.setSelected(true);
          final String basePath = project.getBasePath();
          if (basePath != null && !path.equals(FileUtil.toSystemIndependentName(basePath))) {
            myAssociateCheckbox.setEnabled(false);
            myAssociateCheckbox.setText(PyBundle.message("sdk.edit.dialog.associate.virtual.env.with.path",
                                                         FileUtil.toSystemDependentName(path)));
            myRemoveAssociationLabel.setVisible(true);
          }
        }
      }
    }
    else {
      myAssociateCheckbox.setVisible(false);
    }
    myWasAssociated = myAssociateCheckbox.isSelected();
    init();
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        myAssociateCheckbox.setSelected(false);
        myAssociateCheckbox.setEnabled(true);
        myAssociateCheckbox.setText(PyBundle.message("sdk.edit.dialog.associate.virtual.env.current.project"));
        myRemoveAssociationLabel.setVisible(false);
        myAssociationRemoved = true;
        return true;
      }
    }.installOn(myRemoveAssociationLabel);
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameTextField;
  }

  @NlsSafe
  public String getName() {
    return myNameTextField.getText();
  }

  public String getHomePath() {
    return myInterpreterPathTextField.getText();
  }

  public boolean associateWithProject() {
    return myAssociateCheckbox.isSelected();
  }

  public boolean isAssociateChanged() {
    return myWasAssociated != associateWithProject() || myAssociationRemoved;
  }
}
