// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.execution.target.BrowsableTargetEnvironmentType;
import com.intellij.execution.target.TargetBrowserHints;
import com.intellij.execution.target.TargetBasedSdkAdditionalData;
import com.intellij.execution.target.TargetEnvironmentConfigurationKt;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.NlsContexts;
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
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;


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
    var sdkAdditionalData = sdk.getSdkAdditionalData();
    var label = PyBundle.message("sdk.edit.dialog.specify.interpreter.path");
    var targetListener = createBrowseTargetListener(project, sdkAdditionalData, label);
    if (targetListener != null) {
      myInterpreterPathTextField.addActionListener(targetListener);
    }
    else {
      myInterpreterPathTextField.addBrowseFolderListener(label, null, project,
                                                         PythonSdkType.getInstance().getHomeChooserDescriptor());
    }
    myRemoveAssociationLabel.setVisible(false);
    final PythonSdkFlavor sdkFlavor = PythonSdkFlavor.getPlatformIndependentFlavor(homePath);
    if ((sdkFlavor instanceof VirtualEnvSdkFlavor) || (sdkFlavor instanceof CondaEnvSdkFlavor)) {
      PythonSdkAdditionalData data = (PythonSdkAdditionalData)sdk.getSdkAdditionalData();
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

  /**
   * @return action to call when user clicks on "browse" button if sdk is target SDK that supports browsing
   */
  private @Nullable ActionListener createBrowseTargetListener(@NotNull Project project, @NotNull SdkAdditionalData sdkAdditionalData,
                                                              @NotNull @NlsContexts.DialogTitle String label) {
    if (!(sdkAdditionalData instanceof TargetBasedSdkAdditionalData)) {
      return null;
    }
    var configuration = ((TargetBasedSdkAdditionalData)sdkAdditionalData).getTargetEnvironmentConfiguration();
    if (configuration != null) {
      var type = TargetEnvironmentConfigurationKt.getTargetType(configuration);
      if (type instanceof BrowsableTargetEnvironmentType) {
        return ((BrowsableTargetEnvironmentType)type).createBrowser(project, label, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                                                    myInterpreterPathTextField.getTextField(), () -> configuration, new TargetBrowserHints(true, null));
      }
    }
    return null;
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
