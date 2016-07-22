/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.sdk;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.RemoteSdkCredentialsHolder;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateVirtualEnvDialog extends AbstractCreateVirtualEnvDialog {
  private JComboBox mySdkCombo;
  private JBCheckBox mySitePackagesCheckBox;

  public CreateVirtualEnvDialog(Project project,
                                final List<Sdk> allSdks) {
    super(project, allSdks);
  }

  public CreateVirtualEnvDialog(Component owner, final List<Sdk> allSdks) {
    super(owner, allSdks);
  }

  void setupDialog(Project project, final List<Sdk> allSdks) {
    super.setupDialog(project, allSdks);

    setTitle(PyBundle.message("sdk.create.venv.dialog.title"));

    Iterables.removeIf(allSdks, new Predicate<Sdk>() {
      @Override
      public boolean apply(Sdk s) {
        return PythonSdkType.isInvalid(s) || PythonSdkType.isVirtualEnv(s) || RemoteSdkCredentialsHolder.isRemoteSdk(s.getHomePath()) ||
               PythonSdkType.isCondaVirtualEnv(s);
      }
    });
    List<Sdk> sortedSdks = new ArrayList<>(allSdks);
    Collections.sort(sortedSdks, new PreferredSdkComparator());
    updateSdkList(allSdks, sortedSdks.isEmpty() ? null : sortedSdks.get(0));
  }

  protected void layoutPanel(final List<Sdk> allSdks) {

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(2,2,2,2);

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.name")), c);

    c.gridx = 1;
    c.gridy = 0;
    c.gridwidth = 2;
    c.weightx = 1.0;

    myMainPanel.add(myName, c);

    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.location")), c);

    c.gridx = 1;
    c.gridy = 1;
    c.gridwidth = 2;
    c.weightx = 1.0;
    myMainPanel.add(myDestination, c);

    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.dialog.label.base.interpreter")), c);

    c.gridx = 1;
    c.gridy = 2;
    mySdkCombo = new ComboBox();
    c.insets = new Insets(2,2,2,2);
    c.weightx = 1.0;
    myMainPanel.add(mySdkCombo, c);

    c.gridx = 2;
    c.gridy = 2;
    c.insets = new Insets(0,0,2,2);
    c.weightx = 0.0;
    FixedSizeButton button = new FixedSizeButton();
    button.setPreferredSize(myDestination.getButton().getPreferredSize());
    myMainPanel.add(button, c);

    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 3;
    c.insets = new Insets(2,2,2,2);
    mySitePackagesCheckBox = new JBCheckBox(PyBundle.message("sdk.create.venv.dialog.label.inherit.global.site.packages"));
    myMainPanel.add(mySitePackagesCheckBox, c);

    c.gridx = 0;
    c.gridy = 4;

    myMainPanel.add(myMakeAvailableToAllProjectsCheckbox, c);
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final PySdkService sdkService = PySdkService.getInstance();

        final PythonSdkType sdkType = PythonSdkType.getInstance();
        final FileChooserDescriptor descriptor = sdkType.getHomeChooserDescriptor();

        String suggestedPath = sdkType.suggestHomePath();
        VirtualFile suggestedDir = suggestedPath == null
                                   ? null
                                   : LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(suggestedPath));
        final NullableConsumer<Sdk> consumer = sdk -> {
          if (sdk == null) return;
          if (!allSdks.contains(sdk)) {
            allSdks.add(sdk);
            sdkService.addSdk(sdk);
          }
          updateSdkList(allSdks, sdk);
        };
        FileChooser.chooseFiles(descriptor, myProject, suggestedDir, new FileChooser.FileChooserConsumer() {
          @Override
          public void consume(List<VirtualFile> selectedFiles) {
            String path = selectedFiles.get(0).getPath();
            if (sdkType.isValidSdkHome(path)) {
              path = FileUtil.toSystemDependentName(path);
              Sdk newSdk = null;
              for (Sdk sdk : allSdks) {
                if (path.equals(sdk.getHomePath())) {
                  newSdk = sdk;
                }
              }
              if (newSdk == null) {
                newSdk = new PyDetectedSdk(path);
              }
              consumer.consume(newSdk);
            }
          }

          @Override
          public void cancelled() {
          }
        });

      }
    });
  }

  protected void checkValid() {
    setOKActionEnabled(true);
    setErrorText(null);

    super.checkValid();
    if (mySdkCombo.getSelectedItem() == null) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.dialog.error.no.base.interpreter"));
    }
  }

  protected void registerValidators(final FacetValidatorsManager validatorsManager) {
    super.registerValidators(validatorsManager);

    mySdkCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        validatorsManager.validate();
      }
    });
  }

  private void updateSdkList(final List<Sdk> allSdks, @Nullable Sdk initialSelection) {
    mySdkCombo.setRenderer(new PySdkListCellRenderer(false));
    mySdkCombo.setModel(new CollectionComboBoxModel<>(allSdks, initialSelection));
    checkValid();
  }

  public String getDestination() {
    return myDestination.getText();
  }

  public String getName() {
    return myName.getText();
  }

  public Sdk getSdk() {
    return (Sdk)mySdkCombo.getSelectedItem();
  }

  @Override
  public boolean useGlobalSitePackages() {
    return mySitePackagesCheckBox.isSelected();
  }

  protected String createEnvironment(Sdk basicSdk) throws ExecutionException {
    final PyPackageManager packageManager = PyPackageManager.getInstance(basicSdk);
    return packageManager.createVirtualEnv(getDestination(), useGlobalSitePackages());
  }
}
