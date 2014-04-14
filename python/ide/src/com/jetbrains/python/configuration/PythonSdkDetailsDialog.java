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
package com.jetbrains.python.configuration;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.NullableConsumer;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PythonSdkDetailsDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JList mySdkList;
  private boolean mySdkListChanged = false;
  private final PyConfigurableInterpreterList myInterpreterList;
  private final ProjectSdksModel myProjectSdksModel;

  private Map<Sdk, SdkModificator> myModificators = new FactoryMap<Sdk, SdkModificator>() {
    @Override
    protected SdkModificator create(Sdk sdk) {
      return sdk.getSdkModificator();
    }
  };
  private Set<SdkModificator> myModifiedModificators = new HashSet<SdkModificator>();
  private final Project myProject;

  private boolean myShowOtherProjectVirtualenvs = true;
  private final Module myModule;
  private NullableConsumer<Sdk> myShowMoreCallback;
  private SdkModel.Listener myListener;

  public PythonSdkDetailsDialog(Project project, NullableConsumer<Sdk> showMoreCallback) {
    super(project, true);
    myModule = null;

    setTitle("Project Interpreters");
    myShowMoreCallback = showMoreCallback;
    myProject = project;
    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
    init();
    updateOkButton();
  }

  @Override
  protected void dispose() {
    myProjectSdksModel.removeListener(myListener);
    super.dispose();
  }

  public PythonSdkDetailsDialog(Module module, NullableConsumer<Sdk> showMoreCallback) {
    super(module.getProject());
    myModule = module;

    setTitle("Project Interpreters");
    myShowMoreCallback = showMoreCallback;
    myProject = module.getProject();
    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
    init();
    updateOkButton();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    mySdkList = new JBList();
    //noinspection unchecked
    mySdkList.setCellRenderer(new PySdkListCellRenderer("", myModificators));
    mySdkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mySdkList).disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addSdk(button);
          updateOkButton();
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editSdk();
          updateOkButton();
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeSdk();
          updateOkButton();
        }
      })
      .addExtraAction(new ToggleVirtualEnvFilterButton())
      .addExtraAction(new ShowPathButton());

    decorator.setPreferredSize(new Dimension(600, 500));
    myMainPanel = decorator.createPanel();
    refreshSdkList();
    addListeners();
    return myMainPanel;
  }

  private void addListeners() {
    myListener = new SdkModel.Listener() {
      @Override
      public void sdkAdded(Sdk sdk) {
      }

      @Override
      public void beforeSdkRemove(Sdk sdk) {
      }

      @Override
      public void sdkChanged(Sdk sdk, String previousName) {
        refreshSdkList();
      }

      @Override
      public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      }
    };
    myProjectSdksModel.addListener(myListener);
    mySdkList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent event) {
        updateOkButton();
        updateUI(getSelectedSdk());
      }
    });
  }

  private void updateUI(final Sdk selectedSdk) {
    myProjectSdksModel.setProjectSdk(selectedSdk);
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySdkList;
  }

  public boolean isModified() {
    Sdk projectSdk = getSdk();
    if (projectSdk != null) {
      projectSdk = myProjectSdksModel.findSdk(projectSdk.getName());
    }
    return getSelectedSdk() != projectSdk || mySdkListChanged ||
           myProjectSdksModel.isModified() ||
           !myModifiedModificators.isEmpty();
  }

  protected void updateOkButton() {
    super.setOKActionEnabled(isModified());
  }

  @Override
  protected void doOKAction() {
    try {
      apply();
    }
    catch (ConfigurationException ignored) {
    }
    super.doOKAction();
  }

  public void apply() throws ConfigurationException {
    for (SdkModificator modificator : myModifiedModificators) {
      modificator.commitChanges();
    }
    myModificators.clear();
    myModifiedModificators.clear();
    myProjectSdksModel.apply();
    mySdkListChanged = false;
    myShowMoreCallback.consume(getSelectedSdk());
    Disposer.dispose(getDisposable());
  }

  @Nullable
  public Sdk getSelectedSdk() {
    return (Sdk)mySdkList.getSelectedValue();
  }

  private void refreshSdkList() {
    final List<Sdk> pythonSdks = myInterpreterList.getAllPythonSdks(myProject);
    Sdk projectSdk = getSdk();
    if (!myShowOtherProjectVirtualenvs) {
      VirtualEnvProjectFilter.removeNotMatching(myProject, pythonSdks);
    }
    //noinspection unchecked
    mySdkList.setModel(new CollectionListModel<Sdk>(pythonSdks));

    mySdkListChanged = false;
    if (projectSdk != null) {
      projectSdk = myProjectSdksModel.findSdk(projectSdk.getName());
      mySdkList.clearSelection();
      mySdkList.setSelectedValue(projectSdk, true);
      mySdkList.updateUI();
    }
  }

  @Nullable
  private Sdk getSdk() {
    if (myModule == null) {
      return ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    return rootManager.getSdk();
  }

  private void addSdk(AnActionButton button) {
    PythonSdkDetailsStep
      .show(myProject, myProjectSdksModel.getSdks(), null, myMainPanel, button.getPreferredPopupPoint().getScreenPoint(),
            new NullableConsumer<Sdk>() {
              @Override
              public void consume(Sdk sdk) {
                addCreatedSdk(sdk, true);
              }
            });
  }

  private void addCreatedSdk(@Nullable final Sdk sdk, boolean newVirtualEnv) {
    if (sdk != null) {
      final PySdkService sdkService = PySdkService.getInstance();
      sdkService.restoreSdk(sdk);

      boolean isVirtualEnv = PythonSdkType.isVirtualEnv(sdk);
      if (isVirtualEnv && !newVirtualEnv) {
        AddVEnvOptionsDialog dialog = new AddVEnvOptionsDialog(myMainPanel);
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return;
        }
        SdkModificator modificator = myModificators.get(sdk);
        setSdkAssociated(modificator, !dialog.makeAvailableToAll());
        myModifiedModificators.add(modificator);
      }
      final Sdk oldSdk = myProjectSdksModel.findSdk(sdk);
      if (oldSdk == null)
        myProjectSdksModel.addSdk(sdk);
      refreshSdkList();
      mySdkList.setSelectedValue(sdk, true);
      mySdkListChanged = true;
    }
  }

  private void editSdk() {
    final Sdk currentSdk = getSelectedSdk();
    if (currentSdk != null) {
      if (currentSdk.getSdkAdditionalData() instanceof RemoteSdkAdditionalData) {
        editRemoteSdk(currentSdk);
      }
      else {
        editSdk(currentSdk);
      }
      updateUI(currentSdk);
    }
  }

  private void editRemoteSdk(Sdk currentSdk) {
    PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
    if (remoteInterpreterManager != null) {
      final SdkModificator modificator = myModificators.get(currentSdk);
      Set<Sdk> existingSdks = Sets.newHashSet(myProjectSdksModel.getSdks());
      existingSdks.remove(currentSdk);
      if (remoteInterpreterManager.editSdk(myProject, modificator, existingSdks)) {
        myModifiedModificators.add(modificator);
      }
    }
  }

  private void editSdk(final Sdk currentSdk) {
    final SdkModificator modificator = myModificators.get(currentSdk);
    final EditSdkDialog dialog = new EditSdkDialog(myProject, modificator, new NullableFunction<String, String>() {
      @Override
      public String fun(String s) {
        if (isDuplicateSdkName(s, currentSdk)) {
          return "Please specify a unique name for the interpreter";
        }
        return null;
      }
    });
    dialog.show();
    if (dialog.isOK()) {
      final boolean pathChanged = !Comparing.equal(currentSdk.getHomePath(), dialog.getHomePath());
      if (!currentSdk.getName().equals(dialog.getName()) || pathChanged || dialog.isAssociateChanged()) {
        myModifiedModificators.add(modificator);
        modificator.setName(dialog.getName());
        modificator.setHomePath(dialog.getHomePath());

        if (dialog.isAssociateChanged()) {
          setSdkAssociated(modificator, dialog.associateWithProject());
        }
        if (pathChanged) {
          reloadSdk(currentSdk);
        }
      }
    }
  }

  private void setSdkAssociated(SdkModificator modificator, boolean isAssociated) {
    PythonSdkAdditionalData additionalData = (PythonSdkAdditionalData)modificator.getSdkAdditionalData();
    if (additionalData == null) {
      additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(modificator.getHomePath()));
      modificator.setSdkAdditionalData(additionalData);
    }
    if (isAssociated) {
      additionalData.associateWithProject(myProject);
    }
    else {
      additionalData.setAssociatedProjectPath(null);
    }
  }

  private boolean isDuplicateSdkName(String s, Sdk sdk) {
    for (Sdk existingSdk : myProjectSdksModel.getSdks()) {
      if (existingSdk == sdk) {
        continue;
      }
      String existingName;
      if (myModificators.containsKey(existingSdk)) {
        existingName = myModificators.get(existingSdk).getName();
      }
      else {
        existingName = existingSdk.getName();
      }
      if (existingName.equals(s)) {
        return true;
      }
    }
    return false;
  }

  private void removeSdk() {
    final Sdk currentSdk = getSelectedSdk();
    if (currentSdk != null) {
      final PySdkService sdkService = PySdkService.getInstance();
      sdkService.removeSdk(currentSdk);
      myProjectSdksModel.removeSdk(currentSdk);
      if (myModificators.containsKey(currentSdk)) {
        SdkModificator modificator = myModificators.get(currentSdk);
        myModifiedModificators.remove(modificator);
        myModificators.remove(currentSdk);
      }
      refreshSdkList();
      mySdkListChanged = true;
      // TODO select initially selected SDK
      if (mySdkList.getSelectedIndex() < 0) {
        mySdkList.setSelectedIndex(0);
      }
    }
  }

  private void reloadSdk() {
    final Sdk currentSdk = getSelectedSdk();
    if (currentSdk != null) {
      myModifiedModificators.add(myModificators.get(currentSdk));
      reloadSdk(currentSdk);
    }
  }

  private void reloadSdk(Sdk currentSdk) {
    PythonSdkType.setupSdkPaths(myProject, null, currentSdk, myModificators.get(currentSdk)); // or must it be a RunWriteAction?
  }

  private class ToggleVirtualEnvFilterButton extends ToggleActionButton implements DumbAware {
    public ToggleVirtualEnvFilterButton() {
      super("Show virtual environments associated with other projects", AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myShowOtherProjectVirtualenvs;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myShowOtherProjectVirtualenvs = state;
      refreshSdkList();
      updateOkButton();
    }
  }

  private class ShowPathButton extends AnActionButton implements DumbAware {
    public ShowPathButton() {
      super("Show path for the selected interpreter", AllIcons.Actions.ShowAsTree);
    }

    @Override
    public boolean isEnabled() {
      return getSelectedSdk() != null;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      DialogBuilder dialog = new DialogBuilder(myProject);

      final PythonPathEditor editor =
        new PythonPathEditor("Classes", OrderRootType.CLASSES, FileChooserDescriptorFactory.createAllButJarContentsDescriptor()) {
          @Override
          protected void onReloadButtonClicked() {
            reloadSdk();
          }
        };
      final JComponent component = editor.createComponent();
      component.setPreferredSize(new Dimension(600, 400));
      component.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL));
      dialog.setCenterPanel(component);
      Sdk sdk = getSelectedSdk();
      if (sdk instanceof PyDetectedSdk) {
        final String sdkName = sdk.getName();
        VirtualFile sdkHome = ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
          @Override
          public VirtualFile compute() {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(sdkName);
          }
        });
        sdk = SdkConfigurationUtil.setupSdk(ProjectJdkTable.getInstance().getAllJdks(), sdkHome, PythonSdkType.getInstance(), true, null, null);
      }
      editor.reload(sdk != null ? sdk.getSdkModificator(): null);

      dialog.setTitle("Interpreter Paths");
      dialog.show();
      updateOkButton();
    }
  }
}
