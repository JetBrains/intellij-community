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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remotesdk.RemoteCredentials;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.Consumer;
import com.intellij.util.NullableConsumer;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.FactoryMap;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import icons.PythonIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

public class PythonSdkConfigurable implements Configurable, Configurable.NoScroll {
  private JPanel myPanel;
  private JList mySdkList;
  private JPanel mySplitterHolder;
  private PackagesNotificationPanel myNotificationsArea;
  private JPanel myNotificationsPlaceholder;
  private PythonPathEditor myPathEditor;
  private boolean mySdkListChanged = false;
  private final PyConfigurableInterpreterList myInterpreterList;
  private final ProjectSdksModel myProjectSdksModel;
  private final PyInstalledPackagesPanel myPackagesPanel;

  private Map<Sdk, SdkModificator> myModificators = new FactoryMap<Sdk, SdkModificator>() {
    @Override
    protected SdkModificator create(Sdk sdk) {
      return sdk.getSdkModificator();
    }
  };
  private Set<SdkModificator> myModifiedModificators = new HashSet<SdkModificator>();
  private Sdk myPreviousSelection;
  private boolean myFirstReset;
  private final Project myProject;

  private boolean myNewProject = false;
  private boolean myShowOtherProjectVirtualenvs = false;

  public void setNewProject(final boolean newProject) {
    myNewProject = newProject;
  }

  public PythonSdkConfigurable(Project project) {
    myProject = project;
    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
    myFirstReset = true;


    mySdkList = new JBList();
    mySdkList.setCellRenderer(new PySdkListCellRenderer("", myModificators));
    mySdkList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(mySdkList).disableUpDownActions()
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addSdk(button);
        }
      })
      .setEditAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editSdk();
        }
      })
      .setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeSdk();
        }
      })
      .addExtraAction(new CreateVirtualEnvButton())
      .addExtraAction(new ToggleVirtualEnvFilterButton());


    final Splitter splitter = new Splitter(true);
    /*
    final JScrollPane sdkListPane = ScrollPaneFactory.createScrollPane(mySdkList,
                                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    sdkListPane.setPreferredSize(new Dimension(10, 10));
    */
    splitter.setFirstComponent(decorator.createPanel());

    myPathEditor =
      new PythonPathEditor("Classes", OrderRootType.CLASSES, FileChooserDescriptorFactory.createAllButJarContentsDescriptor()) {
        @Override
        protected void onReloadButtonClicked() {
          reloadSdk();
        }
      };

    myNotificationsArea = new PackagesNotificationPanel(project);
    myNotificationsPlaceholder.add(myNotificationsArea.getComponent(), BorderLayout.CENTER);

    final JBTabbedPane tabbedPane = new JBTabbedPane(SwingConstants.TOP);
    myPackagesPanel = new PyInstalledPackagesPanel(project, myNotificationsArea);
    tabbedPane.addTab("Packages", myPackagesPanel);

    JPanel panel1 = new JPanel(new GridBagLayout());
    Insets anInsets1 = new Insets(2, 2, 2, 2);
    JScrollPane scrollPane1 = ScrollPaneFactory.createScrollPane(myPathEditor.createComponent(),
                                                                 ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                                 ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane1.setPreferredSize(new Dimension(500, 500));
    panel1.add(scrollPane1, new GridBagConstraints(0, 0, 1, 8, 1.0, 1.0,
                                                   GridBagConstraints.CENTER,
                                                   GridBagConstraints.BOTH,
                                                   anInsets1, 0, 0));

    tabbedPane.addTab("Paths", panel1);

    splitter.setSecondComponent(tabbedPane);
    mySplitterHolder.add(splitter, BorderLayout.CENTER);

    addListeners();
  }

  private void addListeners() {
    myProjectSdksModel.addListener(new SdkModel.Listener() {
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
    });
    myPackagesPanel.addPathChangedListener(new Consumer<Sdk>() {
      @Override
      public void consume(Sdk sdk) {
        updateSdkPaths(sdk);
      }
    });

    myNotificationsArea.addLinkHandler(PyInstalledPackagesPanel.CREATE_VENV, new Runnable() {
      @Override
      public void run() {
        createVirtualEnv(getSelectedSdk());
      }
    });
    mySdkList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent event) {
        updateUI(getSelectedSdk());
      }
    });
  }

  private void updateUI(final Sdk selectedSdk) {
    if (myPreviousSelection != null) {
      saveSdkPaths(myPreviousSelection);
    }
    myProjectSdksModel.setProjectSdk(selectedSdk);
    updateSdkPaths(selectedSdk);
    myPreviousSelection = selectedSdk;
    myPackagesPanel.updatePackages(selectedSdk == null ? null : new PyPackageManagementService(myProject, selectedSdk));

    if (selectedSdk != null) {
      myPackagesPanel.updateNotifications(selectedSdk);
    }
  }

  private void createVirtualEnv(Sdk sdk) {
    CreateVirtualEnvDialog.VirtualEnvCallback callback = new CreateVirtualEnvDialog.VirtualEnvCallback() {
      @Override
      public void virtualEnvCreated(Sdk sdk, boolean associateWithProject, boolean setAsProjectInterpreter) {
        PythonSdkType.setupSdkPaths(sdk, myProject, null);
        if (associateWithProject) {
          SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
          if (additionalData == null) {
            additionalData = new PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(sdk.getHomePath()));
            ((ProjectJdkImpl)sdk).setSdkAdditionalData(additionalData);
          }
          if (myNewProject) {
            ((PythonSdkAdditionalData)additionalData).associateWithNewProject();
          }
          else {
            ((PythonSdkAdditionalData)additionalData).associateWithProject(myProject);
          }
        }
        addCreatedSdk(sdk, true, setAsProjectInterpreter);
      }
    };
    final List<Sdk> allSdks = PyConfigurableInterpreterList.getInstance(myProject).getAllPythonSdks();
    final CreateVirtualEnvDialog dialog = new CreateVirtualEnvDialog(myProject, myNewProject, allSdks, sdk);
    dialog.show();
    if (dialog.isOK()) {
      dialog.createVirtualEnv(allSdks, callback);
    }
  }

  public String getDisplayName() {
    return "Python Interpreters";
  }

  public String getHelpTopic() {
    return "python_interpreter";
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    return mySdkListChanged ||
           myProjectSdksModel.isModified() ||
           myPathEditor.isModified() ||
           !myModifiedModificators.isEmpty();
  }

  @Nullable
  private String getSelectedSdkName() {
    final Sdk selectedSdk = (Sdk)mySdkList.getSelectedValue();
    return selectedSdk == null ? null : selectedSdk.getName();
  }

  public void apply() throws ConfigurationException {
    if (myPreviousSelection != null) {
      saveSdkPaths(myPreviousSelection);
    }
    for (SdkModificator modificator : myModifiedModificators) {
      modificator.commitChanges();
    }
    myModificators.clear();
    myModifiedModificators.clear();
    myProjectSdksModel.apply();
    mySdkListChanged = false;
  }

  /**
   * Returns the stable copy of the SDK currently selected in the SDK table.
   *
   * @return the selected SDK, or null if there's no selection
   */
  @Nullable
  public Sdk getRealSelectedSdk() {
    return ProjectJdkTable.getInstance().findJdk(getSelectedSdkName());
  }

  @Nullable
  public Sdk getSelectedSdk() {
    return (Sdk)mySdkList.getSelectedValue();
  }

  public void reset() {
    clearModificators();
    if (myFirstReset) {
      myFirstReset = false;
    }
    else {
      myProjectSdksModel.reset(null);
    }
    refreshSdkList();
    final Sdk selectedSdk = getRealSelectedSdk();
    if (selectedSdk != null) {
      myPackagesPanel.updateNotifications(selectedSdk);
    }
  }

  private void clearModificators() {
    myModificators.clear();
    myModifiedModificators.clear();
    myPreviousSelection = null;
  }

  private void refreshSdkList() {
    final List<Sdk> pythonSdks = myInterpreterList.getAllPythonSdks();
    Sdk projectSdk = myProjectSdksModel.getProjectSdk();
    if (!myShowOtherProjectVirtualenvs) {
      VirtualEnvProjectFilter.removeNotMatching(myProject, pythonSdks);
    }
    Collections.sort(pythonSdks, new PreferredSdkComparator());
    mySdkList.setModel(new CollectionListModel<Sdk>(pythonSdks));

    mySdkListChanged = false;
    if (projectSdk == null) projectSdk = getSdk();
    if (projectSdk != null) {
      projectSdk = myProjectSdksModel.findSdk(projectSdk.getName());
      mySdkList.clearSelection();
      mySdkList.setSelectedValue(projectSdk, true);
      mySdkList.updateUI();
    }
  }

  @Nullable
  private Sdk getSdk() {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    if (modules.length > 0) {
      final Module module = modules[0];
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      return rootManager.getSdk();
    }
    return ProjectRootManager.getInstance(myProject).getProjectSdk();
  }

  private void addSdk(AnActionButton button) {
    InterpreterPathChooser
      .show(myProject, myProjectSdksModel.getSdks(), button.getPreferredPopupPoint(), false, new NullableConsumer<Sdk>() {
        @Override
        public void consume(Sdk sdk) {
          addCreatedSdk(sdk, false, false);
        }
      });
  }

  private void addCreatedSdk(@Nullable final Sdk sdk, boolean newVirtualEnv, boolean makeActive) {
    if (sdk != null) {
      boolean isVirtualEnv = PythonSdkType.isVirtualEnv(sdk);
      boolean askSetAsProjectInterpreter = !myProject.isDefault() && !myNewProject;
      if (askSetAsProjectInterpreter && isVirtualEnv && !newVirtualEnv) {
        AddVEnvOptionsDialog dialog = new AddVEnvOptionsDialog(myPanel);
        dialog.show();
        if (dialog.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
          return;
        }
        SdkModificator modificator = myModificators.get(sdk);
        setSdkAssociated(modificator, !dialog.makeAvailableToAll());
        myModifiedModificators.add(modificator);
        makeActive = dialog.useForThisProject();
      }
      myProjectSdksModel.addSdk(sdk);
      refreshSdkList();
      mySdkList.setSelectedValue(sdk, true);
      mySdkListChanged = true;
      if (askSetAsProjectInterpreter && !isVirtualEnv && !PythonSdkType.isInvalid(sdk) && !PythonSdkType.isIncompleteRemote(sdk)) {
        //TODO: make native mac dialog work
        makeActive = Messages.showIdeaMessageDialog(myProject, "Do you want to set this interpreter as Project Interpreter?",
                                                    "Project Interpreter",
                                                    new String[]{CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()}, 0, null,
                                                    null
        ) == Messages.YES;
      }
      if (makeActive) {
        SdkConfigurationUtil.setDirectoryProjectSdk(myProject, sdk);
        myProjectSdksModel.setProjectSdk(sdk);
        myInterpreterList.setSelectedSdk(sdk);
      }
    }
  }

  private void editSdk() {
    final Sdk currentSdk = getSelectedSdk();
    if (currentSdk != null) {
      if (currentSdk.getSdkAdditionalData() instanceof RemoteCredentials) {
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
    final Sdk current_sdk = getSelectedSdk();
    if (current_sdk != null) {
      myProjectSdksModel.removeSdk(current_sdk);
      if (myModificators.containsKey(current_sdk)) {
        SdkModificator modificator = myModificators.get(current_sdk);
        myModifiedModificators.remove(modificator);
        myModificators.remove(current_sdk);
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
    reloadSdkPaths(currentSdk);
  }

  public void disposeUIResources() {
    myInterpreterList.disposeModel();
    clearModificators();
    myFirstReset = true;
  }

  private void saveSdkPaths(Sdk selection) {
    SdkModificator modificator = myModificators.get(selection);
    if (myPathEditor.isModified()) {
      myPathEditor.apply(modificator);
      myModifiedModificators.add(modificator);
    }
  }

  private void reloadSdkPaths(Sdk selection) {
    List<VirtualFile> rootPaths = Lists.newArrayList();
    if (selection != null) {
      Collections.addAll(rootPaths, selection.getRootProvider().getFiles(OrderRootType.CLASSES));
      myPathEditor.reload(myModificators.get(selection));
    }
    else {
      myPathEditor.reload(null);
    }
  }

  private void updateSdkPaths(final Sdk selection) {
    final List<VirtualFile> rootPaths = Lists.newArrayList();
    if (selection != null) {
      Collections.addAll(rootPaths, selection.getRootProvider().getFiles(OrderRootType.CLASSES));
      myPathEditor.reset(myModificators.get(selection));
    }
    else {
      myPathEditor.reset(null);
    }
  }

  private class CreateVirtualEnvButton extends AnActionButton implements DumbAware {
    public CreateVirtualEnvButton() {
      super("Create Virtual Environment", PythonIcons.Python.Virtualenv);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Sdk selectedSdk = getSelectedSdk();
      createVirtualEnv(selectedSdk);
    }
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
    }
  }
}
