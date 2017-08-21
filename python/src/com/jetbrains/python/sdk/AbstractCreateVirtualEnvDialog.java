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
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.LocationNameFieldsBinding;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.webcore.packaging.PackageManagementService;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageService;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor;
import com.jetbrains.python.ui.IdeaDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Collections;
import java.util.List;

public abstract class AbstractCreateVirtualEnvDialog extends IdeaDialog {
  @Nullable protected Project myProject;
  protected JPanel myMainPanel;
  protected JTextField myName;
  protected TextFieldWithBrowseButton myDestination;
  protected JBCheckBox myMakeAvailableToAllProjectsCheckbox;
  protected String myInitialPath;

  public interface VirtualEnvCallback {
    void virtualEnvCreated(Sdk sdk, boolean associateWithProject);
  }

  public static void setupVirtualEnvSdk(final String path,
                                           boolean associateWithProject,
                                           VirtualEnvCallback callback) {
    final VirtualFile sdkHome =
      WriteAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(path));
    if (sdkHome != null) {
      final Sdk sdk = SdkConfigurationUtil.createAndAddSDK(FileUtil.toSystemDependentName(sdkHome.getPath()), PythonSdkType.getInstance());
      callback.virtualEnvCreated(sdk, associateWithProject);
    }
  }

  public AbstractCreateVirtualEnvDialog(Project project, final List<Sdk> allSdks) {
    super(project);
    setupDialog(project, allSdks);
  }

  public AbstractCreateVirtualEnvDialog(Component owner, final List<Sdk> allSdks) {
    super(owner);
    setupDialog(null, allSdks);
  }

  void setupDialog(Project project, final List<Sdk> allSdks) {
    myProject = project;

    final GridBagLayout layout = new GridBagLayout();
    myMainPanel = new JPanel(layout);
    myName = new JTextField();
    myDestination = new TextFieldWithBrowseButton();
    myMakeAvailableToAllProjectsCheckbox = new JBCheckBox(PyBundle.message("sdk.create.venv.dialog.make.available.to.all.projects"));
    if (project == null || project.isDefault() || !PlatformUtils.isPyCharm()) {
      myMakeAvailableToAllProjectsCheckbox.setSelected(false);
      myMakeAvailableToAllProjectsCheckbox.setVisible(true);
    }

    layoutPanel(allSdks);
    init();
    setOKActionEnabled(false);
    registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
      }

      public void validate() {
        checkValid();
      }
    });
    myMainPanel.setPreferredSize(new Dimension(300, 50));
    checkValid();
    setInitialDestination();
    addUpdater(myName);
    new LocationNameFieldsBinding(project, myDestination, myName, myInitialPath, PyBundle.message("sdk.create.venv.dialog.select.venv.location"));
  }

  protected void setInitialDestination() {
    myInitialPath = "";

    final VirtualFile file = VirtualEnvSdkFlavor.getDefaultLocation();

    if (file != null) {
      myInitialPath = file.getPath();
    }
    else {
      final String savedPath = PyPackageService.getInstance().getVirtualEnvBasePath();
      if (!StringUtil.isEmptyOrSpaces(savedPath)) {
        myInitialPath = savedPath;
      }
      else if (myProject != null) {
        final VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null) {
          myInitialPath = baseDir.getPath();
        }
      }
    }
  }


  protected void registerValidators(final FacetValidatorsManager validatorsManager) {
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
    myName.addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent event) {
        validatorsManager.validate();
      }
    });

    myDestination.getTextField().addCaretListener(new CaretListener() {
      @Override
      public void caretUpdate(CaretEvent event) {
        validatorsManager.validate();
      }
    });
  }

  protected void checkValid() {
    final String projectName = myName.getText();
    final File destFile = new File(getDestination());
    if (destFile.exists()) {
      final String[] content = destFile.list();
      if (content != null && content.length != 0) {
        setOKActionEnabled(false);
        setErrorText(PyBundle.message("sdk.create.venv.dialog.error.not.empty.directory"), myDestination);
        return;
      }
    }
    if (StringUtil.isEmptyOrSpaces(projectName)) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.dialog.error.empty.venv.name"), myName);
      return;
    }
    if (!PathUtil.isValidFileName(projectName)) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.dialog.error.invalid.directory.name"), myName);
      return;
    }
    if (StringUtil.isEmptyOrSpaces(myDestination.getText())) {
      setOKActionEnabled(false);
      setErrorText(PyBundle.message("sdk.create.venv.dialog.error.empty.venv.location"), myDestination);
      return;
    }

    setOKActionEnabled(true);
    setErrorText(null);
  }

  abstract protected void layoutPanel(final List<Sdk> allSdks);

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Nullable
  abstract public Sdk getSdk();

  abstract public boolean useGlobalSitePackages();

  public String getDestination() {
    return myDestination.getText();
  }

  public String getName() {
    return myName.getText();
  }

  public boolean associateWithProject() {
    return !myMakeAvailableToAllProjectsCheckbox.isSelected();
  }

  public void createVirtualEnv(final VirtualEnvCallback callback) {
    final ProgressManager progman = ProgressManager.getInstance();
    final Sdk basicSdk = getSdk();
    final Task.Modal createTask = new Task.Modal(myProject, PyBundle.message("sdk.create.venv.dialog.creating.venv"), false) {
      String myPath;

      public void run(@NotNull final ProgressIndicator indicator) {

        try {
          indicator.setText(PyBundle.message("sdk.create.venv.dialog.creating.venv"));
          myPath = createEnvironment(basicSdk);
        }
        catch (final ExecutionException e) {
          ApplicationManager.getApplication().invokeLater(() -> {
            final PackageManagementService.ErrorDescription description =
              PyPackageManagementService.toErrorDescription(Collections.singletonList(e), basicSdk);
            if (description != null) {
              PackagesNotificationPanel.showError(PyBundle.message("sdk.create.venv.dialog.error.failed.to.create.venv"), description);
            }
          }, ModalityState.any());
        }
      }


      @Override
      public void onSuccess() {
        if (myPath != null) {
          ApplicationManager.getApplication().invokeLater(() -> setupVirtualEnvSdk(myPath, associateWithProject(), callback));
        }
      }
    };
    progman.run(createTask);
  }

  abstract protected String createEnvironment(Sdk basicSdk) throws ExecutionException;

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myName;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    VirtualFile baseDir = myProject != null ? myProject.getBaseDir() : null;
    if (!myDestination.getText().startsWith(myInitialPath) &&
        (baseDir == null || !myDestination.getText().startsWith(baseDir.getPath()))) {
      String path = myDestination.getText();
      PyPackageService.getInstance().setVirtualEnvBasePath(!path.contains(File.separator) ?
                                                           path : path.substring(0, path.lastIndexOf(File.separator)));
    }
  }
}
