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
package com.jetbrains.python.newProject;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.platform.NewDirectoryProjectDialog;
import com.intellij.remotesdk.RemoteSdkData;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.python.PythonSdkChooserCombo;
import com.jetbrains.python.configuration.VirtualEnvProjectFilter;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.RemoteProjectSettings;
import com.jetbrains.python.sdk.PreferredSdkComparator;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.JythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PyPySdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.List;

/**
 * User : catherine
 */
public class PythonNewDirectoryProjectDialog extends NewDirectoryProjectDialog {
  private ComboboxWithBrowseButton mySdkCombo;
  private final JCheckBox myFrameworkCheckbox;
  private boolean myInstallFrameworkChanged;
  private final Project myProject;

  protected PythonNewDirectoryProjectDialog(Project project) {
    super(project);
    myProject = project;

    final List<Sdk> sdks = PythonSdkType.getAllSdks();
    VirtualEnvProjectFilter.removeAllAssociated(sdks);
    Collections.sort(sdks, PreferredSdkComparator.INSTANCE);
    final Sdk preferred = sdks.isEmpty() ? null : sdks.iterator().next();
    mySdkCombo = new PythonSdkChooserCombo(project, sdks, new Condition<Sdk>() {
      @Override
      public boolean value(Sdk sdk) {
        return sdk == preferred;
      }
    });
    final JLabel label = new JBLabel("Interpreter:", SwingConstants.LEFT) {
      @Override
      public Dimension getMinimumSize() {
        return new JLabel("Project name:").getPreferredSize();
      }

      @Override
      public Dimension getPreferredSize() {
        return getMinimumSize();
      }
    };
    label.setLabelFor(mySdkCombo);
    final JPanel placeholder = getPlaceHolder();
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = 0;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.insets = new Insets(0, 0, 0, 10);
    placeholder.add(label, constraints);

    constraints.gridx = 1;
    constraints.gridy = 0;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0;
    constraints.insets = new Insets(0, 0, 0, 0);

    placeholder.add(mySdkCombo, constraints);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        checkValid();
      }
    };

    myFrameworkCheckbox = new JBCheckBox("Install <framework>");
    constraints.gridx = 0;
    constraints.gridy = 1;
    constraints.gridwidth = 2;
    constraints.weightx = 0.0;
    placeholder.add(myFrameworkCheckbox, constraints);
    myFrameworkCheckbox.setVisible(false);

    myFrameworkCheckbox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myInstallFrameworkChanged = true;
        checkValid();
      }
    });

    mySdkCombo.addActionListener(listener);
    mySdkCombo.getComboBox().addActionListener(listener);
    myProjectTypeComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        selectCompatiblePython();
        checkValid();
      }
    });

    mySdkCombo.getComboBox().addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        checkValid();
      }
    });

    final Dimension checkBoxSize = myFrameworkCheckbox.getPreferredSize();
    myRootPane.setPreferredSize(new Dimension(myRootPane.getPreferredSize().width,
                                              myRootPane.getPreferredSize().height + checkBoxSize.height));

    checkValid();
  }

  @Override
  protected Object getEmptyProjectGenerator() {
    return new DirectoryProjectGenerator() {

      @NotNull
      @Nls
      @Override
      public String getName() {
        return "Empty project";
      }

      @Nullable
      @Override
      public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
        if (PythonSdkType.isRemote(getSdk())) {
          PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
          assert manager != null;
          return manager.showRemoteProjectSettingsDialog(baseDir, (RemoteSdkData)getSdk().getSdkAdditionalData());
        }
        else {
          return null;
        }
      }

      @Override
      public void generateProject(@NotNull Project project,
                                  @NotNull VirtualFile baseDir,
                                  Object settings,
                                  @NotNull Module module) {
        if (settings instanceof RemoteProjectSettings) {
          PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
          assert manager != null;
          manager.createDeployment(project, baseDir, (RemoteProjectSettings)settings,
                                   (RemoteSdkData)getSdk().getSdkAdditionalData());
        }
      }

      @NotNull
      @Override
      public ValidationResult validate(@NotNull String baseDirPath) {
        if (PythonSdkType.isRemote(getSdk())) {
          if (PythonRemoteInterpreterManager.getInstance() == null) {
            return new ValidationResult(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
          }
        }
        return ValidationResult.OK;
      }
    };
  }

  @Override
  public void checkValid() {
    super.checkValid();
    Sdk sdk = getSdk();
    if (isOKActionEnabled()) {
      setOKActionEnabled(true);
      setErrorText(null);
      myFrameworkCheckbox.setVisible(false);

      DirectoryProjectGenerator generator = getProjectGenerator();
      final boolean isPy3k = sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K();
      if (sdk != null && PythonSdkType.isRemote(sdk) && !acceptsRemoteSdk(generator)) {
        setOKActionEnabled(false);
        setErrorText("Please choose a local interpreter");
      }
      else if (generator instanceof PyFrameworkProjectGenerator) {
        PyFrameworkProjectGenerator frameworkProjectGenerator = (PyFrameworkProjectGenerator)generator;
        String frameworkName = frameworkProjectGenerator.getFrameworkTitle();
        if (sdk != null && !isFrameworkInstalled(sdk)) {
          final PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(sdk);
          final boolean onlyWithCache =
            PythonSdkFlavor.getFlavor(sdk) instanceof JythonSdkFlavor || PythonSdkFlavor.getFlavor(sdk) instanceof PyPySdkFlavor;
          try {
            if (onlyWithCache && packageManager.cacheIsNotNull() || !onlyWithCache) {
              final PyPackage pip = packageManager.findPackage("pip");
              myFrameworkCheckbox.setText("Install " + frameworkName);
              myFrameworkCheckbox.setMnemonic(frameworkName.charAt(0));
              myFrameworkCheckbox.setVisible(pip != null);
              if (!myInstallFrameworkChanged) {
                myFrameworkCheckbox.setSelected(pip != null);
              }
            }
          }
          catch (PyExternalProcessException e) {
            myFrameworkCheckbox.setVisible(false);
          }
          if (!myFrameworkCheckbox.isSelected()) {
            setErrorText("No " + frameworkName + " support installed in selected interpreter");
            setOKActionEnabled(false);
          }
        }
        if (isPy3k && !((PyFrameworkProjectGenerator)generator).supportsPython3()) {
          setErrorText(frameworkName + " is not supported for the selected interpreter");
          setOKActionEnabled(false);
        }
      }
      if (sdk == null) {
        setOKActionEnabled(false);
        setErrorText("No Python interpreter selected");
      }
    }
  }

  private void selectCompatiblePython() {
    DirectoryProjectGenerator generator = getProjectGenerator();
    if (generator instanceof PyFrameworkProjectGenerator && !((PyFrameworkProjectGenerator)generator).supportsPython3()) {
      Sdk sdk = getSdk();
      if (sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K()) {
        Sdk python2Sdk = PythonSdkType.findPython2Sdk(null);
        if (python2Sdk != null) {
          mySdkCombo.getComboBox().setSelectedItem(python2Sdk);
          mySdkCombo.getComboBox().repaint();
        }
      }
    }
  }

  private boolean isFrameworkInstalled(Sdk sdk) {
    PyFrameworkProjectGenerator projectGenerator = (PyFrameworkProjectGenerator)getProjectGenerator();

    return projectGenerator != null && projectGenerator.isFrameworkInstalled(myProject, sdk);
  }

  private static boolean acceptsRemoteSdk(DirectoryProjectGenerator generator) {
    if (generator instanceof PyFrameworkProjectGenerator) {
      return ((PyFrameworkProjectGenerator)generator).acceptsRemoteSdk();
    }
    return true;
  }

  public boolean installFramework() {
    return myFrameworkCheckbox.isSelected() && myFrameworkCheckbox.isVisible();
  }

  public Sdk getSdk() {
    return (Sdk)mySdkCombo.getComboBox().getSelectedItem();
  }
}
