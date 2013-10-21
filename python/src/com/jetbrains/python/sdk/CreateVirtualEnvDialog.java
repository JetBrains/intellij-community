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
package com.jetbrains.python.sdk;

import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.LocationNameFieldsBinding;
import com.intellij.remotesdk.RemoteSdkDataHolder;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageManagerImpl;
import com.jetbrains.python.packaging.PyPackageService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreateVirtualEnvDialog extends IdeaDialog {
  private JPanel myMainPanel;
  private JComboBox mySdkCombo;
  private TextFieldWithBrowseButton myDestination;
  private JTextField myName;
  private JBCheckBox mySitePackagesCheckBox;
  private JBCheckBox myMakeAvailableToAllProjectsCheckbox;
  private JBCheckBox mySetAsProjectInterpreterCheckbox;
  @Nullable private Project myProject;
  private String myInitialPath;

  public interface VirtualEnvCallback {
    void virtualEnvCreated(Sdk sdk, boolean associateWithProject, boolean setAsProjectInterpreter);
  }

  private static void setupVirtualEnvSdk(List<Sdk> allSdks,
                                         final String path,
                                         boolean associateWithProject,
                                         final boolean makeActive,
                                         VirtualEnvCallback callback) {
    final VirtualFile sdkHome =
      ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
        @Nullable
        public VirtualFile compute() {
          return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        }
      });
    if (sdkHome != null) {
      final String name =
        SdkConfigurationUtil.createUniqueSdkName(PythonSdkType.getInstance(), sdkHome.getPath(), allSdks);
      final ProjectJdkImpl sdk = new ProjectJdkImpl(name, PythonSdkType.getInstance());
      sdk.setHomePath(sdkHome.getPath());
      callback.virtualEnvCreated(sdk, associateWithProject, makeActive);
    }
  }

  public CreateVirtualEnvDialog(Project project,
                                boolean isNewProject,
                                final List<Sdk> allSdks,
                                @Nullable Sdk suggestedBaseSdk) {
    super(project);
    setupDialog(project, isNewProject, allSdks, suggestedBaseSdk);
  }

  public CreateVirtualEnvDialog(Component owner,
                                boolean isNewProject,
                                final List<Sdk> allSdks,
                                @Nullable Sdk suggestedBaseSdk) {
    super(owner);
    setupDialog(null, isNewProject, allSdks, suggestedBaseSdk);
  }

  private void setupDialog(Project project, boolean isNewProject, List<Sdk> allSdks, @Nullable Sdk suggestedBaseSdk) {
    myProject = project;
    init();
    setTitle("Create Virtual Environment");
    if (suggestedBaseSdk == null && allSdks.size() > 0) {
      List<Sdk> sortedSdks = new ArrayList<Sdk>(allSdks);
      Collections.sort(sortedSdks, new PreferredSdkComparator());
      suggestedBaseSdk = sortedSdks.get(0);
    }
    updateSdkList(allSdks, suggestedBaseSdk);

    myMakeAvailableToAllProjectsCheckbox.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
    if (project == null || project.isDefault() || !PlatformUtils.isPyCharm()) {
      myMakeAvailableToAllProjectsCheckbox.setSelected(true);
      myMakeAvailableToAllProjectsCheckbox.setVisible(false);
      mySetAsProjectInterpreterCheckbox.setSelected(false);
      mySetAsProjectInterpreterCheckbox.setVisible(false);
    }
    else if (isNewProject) {
      mySetAsProjectInterpreterCheckbox.setText("Set as project interpreter for the project being created");
    }

    setOKActionEnabled(false);

    myInitialPath = "";

    final VirtualFile file = VirtualEnvSdkFlavor.getDefaultLocation();

    if (file != null)
      myInitialPath = file.getPath();
    else {
      final String savedPath = PyPackageService.getInstance().getVirtualEnvBasePath();
      if (!StringUtil.isEmptyOrSpaces(savedPath))
        myInitialPath = savedPath;
      else if (myProject != null) {
        final VirtualFile baseDir = myProject.getBaseDir();
        if (baseDir != null)
          myInitialPath = baseDir.getPath();
      }
    }

    addUpdater(myName);
    new LocationNameFieldsBinding(project, myDestination, myName, myInitialPath, "Select Location for Virtual Environment");

    registerValidators(new FacetValidatorsManager() {
      public void registerValidator(FacetEditorValidator validator, JComponent... componentsToWatch) {
      }

      public void validate() {
        checkValid();
      }
    });
    myMainPanel.setPreferredSize(new Dimension(300, 50));
    checkValid();
  }

  private void checkValid() {
    final String projectName = myName.getText();
    if (new File(getDestination()).exists()) {
      setOKActionEnabled(false);
      setErrorText("Directory already exists");
      return;
    }
    if (StringUtil.isEmptyOrSpaces(projectName)) {
      setOKActionEnabled(false);
      setErrorText("VirtualEnv name can't be empty");
      return;
    }
    if (!PathUtil.isValidFileName(projectName)) {
      setOKActionEnabled(false);
      setErrorText("Invalid directory name");
      return;
    }
    if (mySdkCombo.getSelectedItem() == null) {
      setOKActionEnabled(false);
      setErrorText("Select base interpreter");
      return;
    }
    if (StringUtil.isEmptyOrSpaces(myDestination.getText())) {
      setOKActionEnabled(false);
      setErrorText("Destination directory can't be empty");
      return;
    }

    setOKActionEnabled(true);
    setErrorText(null);
  }

  private void registerValidators(final FacetValidatorsManager validatorsManager) {
    myDestination.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validatorsManager.validate();
      }
    });

    mySdkCombo.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
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

  private void updateSdkList(final List<Sdk> allSdks, @Nullable Sdk initialSelection) {
    mySdkCombo.setRenderer(new PySdkListCellRenderer());
    List<Sdk> baseSdks = new ArrayList<Sdk>();
    for (Sdk s : allSdks) {
      if (!PythonSdkType.isInvalid(s) && !PythonSdkType.isVirtualEnv(s) && !RemoteSdkDataHolder.isRemoteSdk(s.getHomePath())) {
        baseSdks.add(s);
      }
      else if (s.equals(initialSelection)){
        initialSelection = null;
      }
    }

    mySdkCombo.setModel(new CollectionComboBoxModel(baseSdks, initialSelection));
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  public String getDestination() {
    return myDestination.getText();
  }

  public String getName() {
    return myName.getText();
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

  public Sdk getSdk() {
    return (Sdk)mySdkCombo.getSelectedItem();
  }

  public boolean useGlobalSitePackages() {
    return mySitePackagesCheckBox.isSelected();
  }

  public boolean associateWithProject() {
    return !myMakeAvailableToAllProjectsCheckbox.isSelected();
  }

  public boolean setAsProjectInterpreter() {
    return mySetAsProjectInterpreterCheckbox.isSelected();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myName;
  }

  public void createVirtualEnv(final List<Sdk> allSdks, final VirtualEnvCallback callback) {
    final ProgressManager progman = ProgressManager.getInstance();
    final Sdk basicSdk = getSdk();
    final Task.Modal createTask = new Task.Modal(myProject, "Creating virtual environment for " + basicSdk.getName(), false) {
      String myPath;

      public void run(@NotNull final ProgressIndicator indicator) {
        final PyPackageManagerImpl packageManager = (PyPackageManagerImpl)PyPackageManager.getInstance(basicSdk);
        try {
          indicator.setText("Creating virtual environment for " + basicSdk.getName());
          myPath = packageManager.createVirtualEnv(getDestination(), useGlobalSitePackages());
        }
        catch (final PyExternalProcessException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              packageManager.showInstallationError(getOwner(), "Failed to Create Virtual Environment", e.toString());
            }
          }, ModalityState.any());
        }
      }

      @Override
      public void onSuccess() {
        if (myPath != null) {
          final Application application = ApplicationManager.getApplication();
          application.invokeLater(new Runnable() {
            @Override
            public void run() {
              setupVirtualEnvSdk(allSdks, myPath, associateWithProject(), setAsProjectInterpreter(), callback);
            }
          }, ModalityState.any());
        }
      }
    };
    progman.run(createTask);
  }

}
