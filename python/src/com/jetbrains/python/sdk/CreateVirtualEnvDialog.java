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
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.LocationNameFieldsBinding;
import com.intellij.remote.RemoteSdkCredentialsHolder;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.PyExternalProcessException;
import com.jetbrains.python.packaging.PyPackageManager;
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
  @Nullable private Project myProject;
  private String myInitialPath;

  public interface VirtualEnvCallback {
    void virtualEnvCreated(Sdk sdk, boolean associateWithProject);
  }

  private static void setupVirtualEnvSdk(List<Sdk> allSdks,
                                         final String path,
                                         boolean associateWithProject,
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
      sdk.setHomePath(FileUtil.toSystemDependentName(sdkHome.getPath()));
      callback.virtualEnvCreated(sdk, associateWithProject);
    }
  }

  public CreateVirtualEnvDialog(Project project,
                                final List<Sdk> allSdks,
                                @Nullable Sdk suggestedBaseSdk) {
    super(project);
    setupDialog(project, allSdks, suggestedBaseSdk);
  }

  public CreateVirtualEnvDialog(Component owner,
                                final List<Sdk> allSdks,
                                @Nullable Sdk suggestedBaseSdk) {
    super(owner);
    setupDialog(null, allSdks, suggestedBaseSdk);
  }

  private void setupDialog(Project project, final List<Sdk> allSdks, @Nullable Sdk suggestedBaseSdk) {
    myProject = project;
    layoutPanel(allSdks);

    init();
    setTitle("Create Virtual Environment");
    Iterables.removeIf(allSdks, new Predicate<Sdk>() {
      @Override
      public boolean apply(Sdk s) {
        return PythonSdkType.isInvalid(s) || PythonSdkType.isVirtualEnv(s) || RemoteSdkCredentialsHolder.isRemoteSdk(s.getHomePath());
      }
    });
    if (suggestedBaseSdk == null && allSdks.size() > 0) {
      List<Sdk> sortedSdks = new ArrayList<Sdk>(allSdks);
      Collections.sort(sortedSdks, new PreferredSdkComparator());
      suggestedBaseSdk = sortedSdks.get(0);
    }
    updateSdkList(allSdks, suggestedBaseSdk);

    if (project == null || project.isDefault() || !PlatformUtils.isPyCharm()) {
      myMakeAvailableToAllProjectsCheckbox.setSelected(true);
      myMakeAvailableToAllProjectsCheckbox.setVisible(false);
    }

    setOKActionEnabled(false);

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

  private void layoutPanel(final List<Sdk> allSdks) {
    final GridBagLayout layout = new GridBagLayout();
    myMainPanel = new JPanel(layout);

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = new Insets(2,2,2,2);

    c.gridx = 0;
    c.gridy = 0;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel("Name:"), c);

    c.gridx = 1;
    c.gridy = 0;
    c.gridwidth = 2;
    c.weightx = 1.0;
    myName = new JTextField();
    myMainPanel.add(myName, c);

    c.gridx = 0;
    c.gridy = 1;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel("Location:"), c);

    c.gridx = 1;
    c.gridy = 1;
    c.gridwidth = 2;
    c.weightx = 1.0;
    myDestination = new TextFieldWithBrowseButton();
    myMainPanel.add(myDestination, c);

    c.gridx = 0;
    c.gridy = 2;
    c.gridwidth = 1;
    c.weightx = 0.0;
    myMainPanel.add(new JBLabel("Base interpreter:"), c);

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
    mySitePackagesCheckBox = new JBCheckBox("Inherit global site-packages");
    myMainPanel.add(mySitePackagesCheckBox, c);

    c.gridx = 0;
    c.gridy = 4;
    myMakeAvailableToAllProjectsCheckbox = new JBCheckBox("Make available to all projects");
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
        final NullableConsumer<Sdk> consumer = new NullableConsumer<Sdk>() {
          @Override
          public void consume(@Nullable Sdk sdk) {
            if (sdk == null) return;
            if (!allSdks.contains(sdk)) {
              allSdks.add(sdk);
              sdkService.addSdk(sdk);
            }
            updateSdkList(allSdks, sdk);
          }
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
    mySdkCombo.setRenderer(new PySdkListCellRenderer(false));
    mySdkCombo.setModel(new CollectionComboBoxModel(allSdks, initialSelection));
    checkValid();
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
        final PyPackageManager packageManager = PyPackageManager.getInstance(basicSdk);
        try {
          indicator.setText("Creating virtual environment for " + basicSdk.getName());
          myPath = packageManager.createVirtualEnv(getDestination(), useGlobalSitePackages());
        }
        catch (final PyExternalProcessException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              PackagesNotificationPanel.showError(getOwner(), "Failed to Create Virtual Environment", e.toString());
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
              setupVirtualEnvSdk(allSdks, myPath, associateWithProject(), callback);
            }
          }, ModalityState.any());
        }
      }
    };
    progman.run(createTask);
  }

}
