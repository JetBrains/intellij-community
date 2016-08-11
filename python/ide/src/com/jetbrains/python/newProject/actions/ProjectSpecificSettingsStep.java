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

import com.intellij.facet.ui.ValidationResult;
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase;
import com.intellij.ide.util.projectWizard.WebProjectTemplate;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HideableDecorator;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jetbrains.python.PythonSdkChooserCombo;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.configuration.VirtualEnvProjectFilter;
import com.jetbrains.python.newProject.PyFrameworkProjectGenerator;
import com.jetbrains.python.newProject.PythonProjectGenerator;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
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
    if (!(myProjectGenerator instanceof PythonProjectGenerator)) return null;
    if (mySdk != null) return mySdk;
    if (((PythonProjectGenerator)myProjectGenerator).hideInterpreter()) return null;
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
    if (myProjectGenerator instanceof PythonProjectGenerator && !((PythonProjectGenerator)myProjectGenerator).hideInterpreter()) {
      myLocationField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
        @Override
        protected void textChanged(DocumentEvent e) {
          final String path = myLocationField.getText().trim();
          ((PythonProjectGenerator)myProjectGenerator).locationChanged(PathUtil.getFileName(path));
        }
      });
      
      mySdkCombo.getComboBox().addItemListener(e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) {
          checkValid();
        }
      });
      UiNotifyConnector.doWhenFirstShown(mySdkCombo, this::checkValid);
    }
  }
  
  @Override
  protected void initGeneratorListeners() {
    super.initGeneratorListeners();
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      ((PythonProjectGenerator)myProjectGenerator).addSettingsStateListener(this::checkValid);
      myErrorLabel.addMouseListener(((PythonProjectGenerator)myProjectGenerator).getErrorLabelMouseListener());
    }
  }

  @Override
  public boolean checkValid() {
    myInstallFramework = false;
    if (!super.checkValid()) {
      return false;
    }

    if (myProjectGenerator instanceof PythonProjectGenerator) {
      final Sdk sdk = getSdk();
      if (sdk == null) {
        if (!((PythonProjectGenerator)myProjectGenerator).hideInterpreter()) {
          setErrorText("No Python interpreter selected");
          return false;
        }
        return true;
      }
      else if (PythonSdkType.isInvalid(sdk)) {
        setErrorText("Choose valid python interpreter");
        return false;
      }
      final List<String> warningList = new ArrayList<>();
      final boolean isPy3k = PythonSdkType.getLanguageLevelForSdk(sdk).isPy3K();
      if (PythonSdkType.isRemote(sdk) && !acceptsRemoteSdk(myProjectGenerator)) {
        setErrorText("Please choose a local interpreter");
        return false;
      }
      else if (myProjectGenerator instanceof PyFrameworkProjectGenerator) {
        PyFrameworkProjectGenerator frameworkProjectGenerator = (PyFrameworkProjectGenerator)myProjectGenerator;
        String frameworkName = frameworkProjectGenerator.getFrameworkTitle();
        if (!isFrameworkInstalled(sdk)) {
          if (PyPackageUtil.packageManagementEnabled(sdk)) {
            myInstallFramework = true;
            final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
            if (packages == null) {
              warningList.add(frameworkName + " will be installed on the selected interpreter");
              return false;
            }
            if (!PyPackageUtil.hasManagement(packages)) {
              warningList.add("Python packaging tools and " + frameworkName + " will be installed on the selected interpreter");
            }
            else {
              warningList.add(frameworkName + " will be installed on the selected interpreter");

            }
          } else {
            warningList.add(frameworkName + " is not installed on the selected interpreter");
          }
        }
        final ValidationResult warningResult = ((PythonProjectGenerator)myProjectGenerator).warningValidation(sdk);
        if (!warningResult.isOk()) {
          warningList.add(warningResult.getErrorMessage());
        }

        if (!warningList.isEmpty()) {
          final String warning = StringUtil.join(warningList, "<br/>");
          setWarningText(warning);
        }
        if (isPy3k && !((PyFrameworkProjectGenerator)myProjectGenerator).supportsPython3()) {
          setErrorText(frameworkName + " is not supported for the selected interpreter");
          return false;
        }
      }
    }
    return true;
  }

  private boolean isFrameworkInstalled(Sdk sdk) {
    PyFrameworkProjectGenerator projectGenerator = (PyFrameworkProjectGenerator)getProjectGenerator();
    return projectGenerator != null && projectGenerator.isFrameworkInstalled(sdk);
  }

  @Override
  protected JPanel createBasePanel() {
    if (myProjectGenerator instanceof PythonProjectGenerator) {
      final BorderLayout layout = new BorderLayout();

      final JPanel locationPanel = new JPanel(layout);

      final JPanel panel = new JPanel(new VerticalFlowLayout(0, 2));
      final LabeledComponent<TextFieldWithBrowseButton> location = createLocationComponent();

      locationPanel.add(location, BorderLayout.CENTER);
      panel.add(locationPanel);
      if (((PythonProjectGenerator)myProjectGenerator).hideInterpreter()) {
        addInterpreterButton(locationPanel, location);
      }
      else {
        final LabeledComponent<PythonSdkChooserCombo> labeled = createInterpreterCombo();
        UIUtil.mergeComponentsWithAnchor(labeled, location);
        panel.add(labeled);
      }

      final JPanel basePanelExtension = ((PythonProjectGenerator)myProjectGenerator).extendBasePanel();
      if (basePanelExtension != null) {
        panel.add(basePanelExtension);
      }
      return panel;
    }

    return super.createBasePanel();
  }

  private void addInterpreterButton(final JPanel locationPanel, final LabeledComponent<TextFieldWithBrowseButton> location) {
    final JButton interpreterButton = new FixedSizeButton(location);
    if (SystemInfo.isMac && !UIUtil.isUnderDarcula())
      interpreterButton.putClientProperty("JButton.buttonType", null);
    interpreterButton.setIcon(PythonIcons.Python.Python);
    interpreterButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final DialogBuilder builder = new DialogBuilder();
        final JPanel panel = new JPanel();
        final LabeledComponent<PythonSdkChooserCombo> interpreterCombo = createInterpreterCombo();
        if (mySdk != null) {
          mySdkCombo.getComboBox().setSelectedItem(mySdk);
        }
        panel.add(interpreterCombo);
        builder.setCenterPanel(panel);
        builder.setTitle("Select Python Interpreter");
        if (builder.showAndGet()) {
          mySdk = (Sdk)mySdkCombo.getComboBox().getSelectedItem();
        }
      }
    });
    locationPanel.add(interpreterButton, BorderLayout.EAST);
  }

  @NotNull
  private LabeledComponent<PythonSdkChooserCombo> createInterpreterCombo() {
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
    mySdkCombo = new PythonSdkChooserCombo(project, sdks, sdk -> sdk == preferred);
    if (SystemInfo.isMac && !UIUtil.isUnderDarcula())
      mySdkCombo.putClientProperty("JButton.buttonType", null);
    mySdkCombo.setButtonIcon(PythonIcons.Python.InterpreterGear);

    return LabeledComponent.create(mySdkCombo, "Interpreter", BorderLayout.WEST);
  }
}
