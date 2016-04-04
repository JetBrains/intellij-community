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
package com.jetbrains.python.newProject.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.PythonSdkChooserCombo;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.configuration.VirtualEnvProjectFilter;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;

public class ProjectSpecificSettingsStep extends ProjectSettingsStepBase implements DumbAware {
  private PythonSdkChooserCombo mySdkCombo;
  private boolean myInstallFramework;
  private Sdk mySdk;

  public ProjectSpecificSettingsStep(@NotNull final DirectoryProjectGenerator projectGenerator,
                                     @NotNull final NullableConsumer<ProjectSettingsStepBase> callback) {
    super(projectGenerator, callback);
  }

  private static boolean acceptsRemoteSdk(DirectoryProjectGenerator generator) {
    if (generator instanceof PyFrameworkProjectGenerator) {
      return ((PyFrameworkProjectGenerator)generator).acceptsRemoteSdk();
    }
    return true;
  }

  @Override
  protected JPanel createAndFillContentPanel() {
    return createContentPanelWithAdvancedSettingsPanel();
  }

  @Override
  @Nullable
  protected JPanel createAdvancedSettings() {
    JComponent advancedSettings = null;
    if (myProjectGenerator instanceof PythonProjectGenerator)
      advancedSettings = ((PythonProjectGenerator)myProjectGenerator).getSettingsPanel(myProjectDirectory);
    else if (myProjectGenerator instanceof WebProjectTemplate) {
      advancedSettings = ((WebProjectTemplate)myProjectGenerator).getPeer().getComponent();
    }
    if (advancedSettings != null) {
      final JPanel jPanel = new JPanel(new VerticalFlowLayout());
      final HideableDecorator deco = new HideableDecorator(jPanel, "Mor&e Settings", false);
      boolean isValid = checkValid();
      deco.setOn(!isValid);
      if (myProjectGenerator instanceof PythonProjectGenerator && !deco.isExpanded()) {
        final ValidationResult result = ((PythonProjectGenerator)myProjectGenerator).warningValidation(getSdk());
        deco.setOn(!result.isOk());
      }
      deco.setContentComponent(advancedSettings);
      return jPanel;
    }
    return null;
  }

  public Sdk getSdk() {
    if (mySdk != null) return mySdk;
    return (Sdk)mySdkCombo.getComboBox().getSelectedItem();
  }

  public void setSdk(final Sdk sdk) {
    mySdk = sdk;
  }

  public boolean installFramework() {
    return myInstallFramework;
  }

  @Override
  protected void registerValidators() {
    super.registerValidators();
    mySdkCombo.getComboBox().addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent event) {
        checkValid();
      }
    });
    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        checkValid();
      }
    };
    mySdkCombo.getComboBox().addActionListener(listener);
    mySdkCombo.addActionListener(listener);
  }

  @Nullable
  protected JPanel extendBasePanel() {
    if (myProjectGenerator instanceof PythonProjectGenerator)
      return ((PythonProjectGenerator)myProjectGenerator).extendBasePanel();
    return null;
  }

  @Override
  public boolean checkValid() {
    myInstallFramework = false;
    if (!super.checkValid()) {
      return false;
    }

    final Sdk sdk = getSdk();

    if (myProjectGenerator instanceof PythonProjectGenerator) {
      final ValidationResult warningResult = ((PythonProjectGenerator)myProjectGenerator).warningValidation(sdk);
      if (!warningResult.isOk()) {
        setWarningText(warningResult.getErrorMessage());
      }
    }

    final boolean isPy3k = sdk != null && PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K();
    if (sdk != null && PythonSdkType.isRemote(sdk) && !acceptsRemoteSdk(myProjectGenerator)) {
      setErrorText("Please choose a local interpreter");
      return false;
    }
    else if (myProjectGenerator instanceof PyFrameworkProjectGenerator) {
      PyFrameworkProjectGenerator frameworkProjectGenerator = (PyFrameworkProjectGenerator)myProjectGenerator;
      String frameworkName = frameworkProjectGenerator.getFrameworkTitle();
      if (sdk != null && !isFrameworkInstalled(sdk)) {
        if (PyPackageUtil.packageManagementEnabled(sdk)) {
          String warningText = frameworkName + " will be installed on the selected interpreter";
          myInstallFramework = true;
          final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
          boolean hasManagement = false;
          try {
            hasManagement = packageManager.hasManagement(PySdkUtil.isRemote(sdk));
          }
          catch (ExecutionException ignored) {
          }
          if (!hasManagement) {
            warningText = "Python packaging tools and " + warningText;
          }
          setWarningText(warningText);
        } else {
          setWarningText(frameworkName + " is not installed on the selected interpreter");
        }
      }
      if (isPy3k && !((PyFrameworkProjectGenerator)myProjectGenerator).supportsPython3()) {
        setErrorText(frameworkName + " is not supported for the selected interpreter");
        return false;
      }
    }
    if (sdk == null) {
      setErrorText("No Python interpreter selected");
      return false;
    }
    return true;
  }

  private boolean isFrameworkInstalled(Sdk sdk) {
    PyFrameworkProjectGenerator projectGenerator = (PyFrameworkProjectGenerator)getProjectGenerator();
    return projectGenerator != null && projectGenerator.isFrameworkInstalled(sdk);
  }

  @Override
  protected JPanel createBasePanel() {
    final JPanel panel = super.createBasePanel();

    final Project project = ProjectManager.getInstance().getDefaultProject();
    final List<Sdk> sdks = PyConfigurableInterpreterList.getInstance(project).getAllPythonSdks();
    VirtualEnvProjectFilter.removeAllAssociated(sdks);
    Sdk compatibleSdk = sdks.isEmpty() ? null : sdks.iterator().next();
    DirectoryProjectGenerator generator = getProjectGenerator();
    if (generator instanceof PyFrameworkProjectGenerator && !((PyFrameworkProjectGenerator)generator).supportsPython3()) {
      if (compatibleSdk != null && PythonSdkType.getLanguageLevelForSdk(compatibleSdk).isPy3K()) {
        Sdk python2Sdk = PythonSdkType.findPython2Sdk(sdks);
        if (python2Sdk != null) {
          compatibleSdk = python2Sdk;
        }
      }
    }

    final Sdk preferred = compatibleSdk;
    mySdkCombo = new PythonSdkChooserCombo(project, sdks, new Condition<Sdk>() {
      @Override
      public boolean value(Sdk sdk) {
        return sdk == preferred;
      }
    });
    mySdkCombo.setButtonIcon(PythonIcons.Python.InterpreterGear);

    final LabeledComponent<PythonSdkChooserCombo> labeled = LabeledComponent.create(mySdkCombo, "Interpreter");
    labeled.setLabelLocation(BorderLayout.WEST);
    UIUtil.mergeComponentsWithAnchor(labeled, (PanelWithAnchor)panel.getComponent(0));
    panel.add(labeled);
    final JPanel basePanelExtension = extendBasePanel();
    if (basePanelExtension != null) {
      panel.add(basePanelExtension);
    }

    return panel;
  }

  @Override
  protected void initGeneratorListeners() {
    super.initGeneratorListeners();
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      ((PythonProjectGenerator)myProjectGenerator).addSettingsStateListener(new PythonProjectGenerator.SettingsListener() {
        @Override
        public void stateChanged() {
          checkValid();
        }
      });
    }
    myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (myProjectGenerator instanceof PythonProjectGenerator) {
          String path = myLocationField.getText().trim();
          path = StringUtil.trimEnd(path, File.separator);
          int ind = path.lastIndexOf(File.separator);
          if (ind != -1) {
            String projectName = path.substring(ind + 1, path.length());
            ((PythonProjectGenerator)myProjectGenerator).locationChanged(projectName);
          }
        }
      }
    });
  }
}
