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
package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.packaging.PyCondaPackageService;
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor;
import com.jetbrains.python.validation.UnsupportedFeaturesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;

public class CreateCondaEnvDialog extends AbstractCreateVirtualEnvDialog {
  private JComboBox mySdkCombo;

  public CreateCondaEnvDialog(Project project) {
    super(project, null);
  }

  public CreateCondaEnvDialog(Component owner) {
    super(owner, null);
  }

  protected void setupDialog(Project project, final List<Sdk> allSdks) {
    super.setupDialog(project, allSdks);
    setTitle(PyBundle.message("sdk.create.venv.conda.dialog.title"));
    final List<String> pythonVersions = UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS;
    Collections.reverse(pythonVersions);
    mySdkCombo.setModel(new CollectionComboBoxModel<>(pythonVersions));
    mySdkCombo.setSelectedItem("3.5");
    checkValid();
  }

  @Override
  protected void setInitialDestination() {
    final List<VirtualFile> locations = CondaEnvSdkFlavor.getCondaDefaultLocations();
    if (!locations.isEmpty()) {
      myInitialPath = locations.get(0).getPath();
      return;
    }
    else {
      final String conda = PyCondaPackageService.getSystemCondaExecutable();
      if (conda != null) {
        final VirtualFile condaFile = LocalFileSystem.getInstance().findFileByPath(conda);
        if (condaFile != null) {
          final VirtualFile condaDir = condaFile.getParent().getParent();
          final VirtualFile envs = condaDir.findChild("envs");
          if (envs != null) {
            myInitialPath = envs.getPath();
            return;
          }
        }
      }
    }
    super.setInitialDestination();
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
    myMainPanel.add(new JBLabel(PyBundle.message("sdk.create.venv.conda.dialog.label.python.version")), c);

    c.gridx = 1;
    c.gridy = 2;
    mySdkCombo = new ComboBox();
    c.insets = new Insets(2,2,2,2);
    c.weightx = 1.0;
    myMainPanel.add(mySdkCombo, c);

    c.gridx = 0;
    c.gridy = 3;
    c.gridwidth = 3;
    myMainPanel.add(myMakeAvailableToAllProjectsCheckbox, c);

  }

  @Nullable
  @Override
  public Sdk getSdk() {
    return null;
  }

  @Override
  public boolean useGlobalSitePackages() {
    return false;
  }

  protected void checkValid() {
    setOKActionEnabled(true);
    setErrorText(null);

    super.checkValid();
    if (mySdkCombo.getSelectedItem() == null) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.conda.dialog.error.no.python.version"), mySdkCombo);
    }
  }

  protected void registerValidators(final FacetValidatorsManager validatorsManager) {
    mySdkCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        validatorsManager.validate();
      }
    });
    myDestination.getTextField().addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent event) {
        validatorsManager.validate();
      }
    });
    myDestination.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validatorsManager.validate();
      }
    });

    myDestination.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        validatorsManager.validate();
      }
    });
  }

  @NotNull
  protected String createEnvironment(Sdk basicSdk) throws ExecutionException {
    return PyCondaPackageManagerImpl.createVirtualEnv(getDestination(), (String)mySdkCombo.getSelectedItem());
  }
}
