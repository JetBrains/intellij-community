// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.NullableConsumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public class PythonSdkDetailsDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PythonSdkDetailsDialog.class);

  private boolean myModified = false;

  @NotNull
  private final Project myProject;

  @Nullable
  private final Module myModule;

  @NotNull
  private final NullableConsumer<? super Sdk> mySelectedSdkCallback;

  @NotNull
  private final Consumer<Boolean> myCancelCallback;

  @NotNull
  private final SdkModel.Listener mySdkModelListener;

  @NotNull
  private final PyConfigurableInterpreterList myInterpreterList;

  @NotNull
  private final ProjectSdksModel myProjectSdksModel;

  @NotNull
  private final JBList<Sdk> mySdkList;

  @NotNull
  private final JPanel myMainPanel;

  private boolean myHideOtherProjectVirtualenvs = false;

  public PythonSdkDetailsDialog(@NotNull Project project,
                                @Nullable Module module,
                                @NotNull NullableConsumer<? super Sdk> selectedSdkCallback,
                                @NotNull Consumer<Boolean> cancelCallback) {
    super(project);
    setTitle(PyBundle.message("sdk.details.dialog.title"));

    myProject = project;
    myModule = module;
    mySelectedSdkCallback = selectedSdkCallback;
    myCancelCallback = cancelCallback;

    // there is an assumption that dialog started with unmodified sdks model
    // otherwise processing `Cancel` will be more complicated
    // to correctly revert changes
    mySdkModelListener = new MySdkModelListener();
    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
    myProjectSdksModel.addListener(mySdkModelListener);

    mySdkList = buildSdkList(e -> updateOkButton());
    myMainPanel = buildPanel(
      mySdkList,
      new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          addSdk();
          updateOkButton();
        }
      },
      new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          editSdk();
          updateOkButton();
        }
      },
      new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          removeSdk();
          updateOkButton();
        }
      },
      new ToggleVirtualEnvFilterButton(),
      new ShowPathButton()
    );

    init();
    refreshSdkList();
    updateOkButton();
  }

  @Override
  protected void dispose() {
    myProjectSdksModel.removeListener(mySdkModelListener);
    super.dispose();
  }

  @NotNull
  private static JBList<Sdk> buildSdkList(@NotNull ListSelectionListener selectionListener) {
    final JBList<Sdk> result = new JBList<>();
    result.setCellRenderer(new PySdkListCellRenderer());
    result.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    result.addListSelectionListener(selectionListener);
    ListSpeedSearch.installOn(result);
    return result;
  }

  @NotNull
  private static JPanel buildPanel(@NotNull JBList<Sdk> sdkList,
                                   @NotNull AnActionButtonRunnable addAction,
                                   @NotNull AnActionButtonRunnable editAction,
                                   @NotNull AnActionButtonRunnable removeAction,
                                   AnAction @NotNull ... extraActions) {
    return ToolbarDecorator.createDecorator(sdkList)
      .disableUpDownActions()
      .setAddAction(addAction)
      .setEditAction(editAction)
      .setRemoveAction(removeAction)
      .addExtraActions(extraActions)
      .setPreferredSize(new Dimension(600, 500))
      .createPanel();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySdkList;
  }

  private void updateOkButton() {
    super.setOKActionEnabled(myModified || myProjectSdksModel.isModified() || isAnotherSdkSelected());
  }

  /**
   * Checks whether the selection has changed from the initial one.
   * <p>
   * Note that multiple selection is ambiguous to treat it as the indication for the current project interpreter.
   *
   * @return {@code true} if the selection has changed and {@code false} otherwise
   */
  private boolean isAnotherSdkSelected() {
    if (mySdkList.getSelectedValuesList().size() > 1) {
      return false;
    }
    Sdk originalSelectedSdk = getOriginalSelectedSdk();
    return originalSelectedSdk != null && originalSelectedSdk != getSdk();
  }

  @Override
  protected void doOKAction() {
    apply();
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    final boolean modified = myModified || myProjectSdksModel.isModified();
    if (modified) {
      myProjectSdksModel.reset(myProject);
    }
    myModified = false;
    myCancelCallback.accept(modified);
    super.doCancelAction();
  }

  private void apply() {
    try {
      myProjectSdksModel.apply();
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
    myModified = false;
    final Sdk sdk = getOriginalSelectedSdk();
    mySelectedSdkCallback.consume(sdk);
    if (sdk != null) {
      PyPackageManagers.getInstance().clearCache(sdk);
    }
    Disposer.dispose(getDisposable());
  }

  @Nullable
  private Sdk getOriginalSelectedSdk() {
    final Sdk editableSdk = getEditableSelectedSdk();
    return editableSdk == null ? null : myProjectSdksModel.findSdk(editableSdk);
  }

  @Nullable
  private Sdk getEditableSelectedSdk() {
    return getTheOnlyItemOrNull(mySdkList.getSelectedValuesList());
  }

  @Nullable
  private static <T> T getTheOnlyItemOrNull(@NotNull List<T> collection) {
    if (collection.size() == 1) {
      return collection.get(0);
    }
    else {
      return null;
    }
  }

  private void refreshSdkList() {
    final List<Sdk> allPythonSdks = myInterpreterList.getAllPythonSdks(myProject, myModule);
    Sdk projectSdk = getSdk();
    final List<Sdk> notAssociatedWithOtherProjects = StreamEx
      .of(allPythonSdks)
      .filter(sdk -> !PySdkExtKt.isAssociatedWithAnotherModule(sdk, myModule))
      .toList();

    final List<Sdk> pythonSdks = myHideOtherProjectVirtualenvs ? notAssociatedWithOtherProjects : allPythonSdks;
    mySdkList.setModel(new CollectionListModel<>(pythonSdks));

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

  private void addSdk() {
    PyAddSdkDialog.show(myProject, myModule, Arrays.asList(myProjectSdksModel.getSdks()), new SdkAddedCallback());
  }

  private void setSelectedSdk(@Nullable Sdk selectedSdk) {
    mySdkList.setSelectedValue(selectedSdk == null ? null : myProjectSdksModel.findSdk(selectedSdk.getName()), true);
  }

  private void editSdk() {
    final Sdk currentSdk = getEditableSelectedSdk();
    if (currentSdk != null) {
      if (currentSdk.getSdkAdditionalData() instanceof RemoteSdkAdditionalData) {
        editRemoteSdk(currentSdk);
      }
      else {
        editSdk(currentSdk);
      }
    }
  }

  private void editRemoteSdk(Sdk currentSdk) {
    PythonRemoteInterpreterManager remoteInterpreterManager = PythonRemoteInterpreterManager.getInstance();
    if (remoteInterpreterManager != null) {
      final SdkModificator modificator = currentSdk.getSdkModificator();
      Set<Sdk> existingSdks = Sets.newHashSet(myProjectSdksModel.getSdks());
      existingSdks.remove(currentSdk);
      if (remoteInterpreterManager.editSdk(myProject, modificator, existingSdks)) {
        commitAndRefresh(modificator);
      }
    }
  }

  private void editSdk(final Sdk currentSdk) {
    final SdkModificator modificator = currentSdk.getSdkModificator();
    final EditSdkDialog dialog = new EditSdkDialog(myProject, modificator, s -> {
      if (isDuplicateSdkName(s, currentSdk)) {
        return PyBundle.message("sdk.details.dialog.error.duplicate.name");
      }
      return null;
    });
    if (dialog.showAndGet()) {
      final boolean pathChanged = !Objects.equals(currentSdk.getHomePath(), dialog.getHomePath());
      if (!modificator.getName().equals(dialog.getName()) || pathChanged || dialog.isAssociateChanged()) {
        modificator.setName(dialog.getName());
        modificator.setHomePath(dialog.getHomePath());

        if (dialog.isAssociateChanged()) {
          setSdkAssociated(modificator, dialog.associateWithProject());
        }
        commitAndRefresh(modificator);
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
    if (isAssociated && myModule != null) {
      additionalData.associateWithModule(myModule);
    }
    else if (isAssociated) {
      additionalData.setAssociatedModulePath(myProject.getBasePath());
    }
    else {
      additionalData.resetAssociatedModulePath();
    }
  }

  private boolean isDuplicateSdkName(String s, Sdk sdk) {
    for (Sdk existingSdk : myProjectSdksModel.getSdks()) {
      if (existingSdk == sdk) {
        continue;
      }
      if (existingSdk.getName().equals(s)) {
        return true;
      }
    }
    return false;
  }

  private void removeSdk() {
    final List<Sdk> selectedSdks = mySdkList.getSelectedValuesList();
    if (!selectedSdks.isEmpty()) {
      selectedSdks.forEach(selectedSdk -> myProjectSdksModel.removeSdk(selectedSdk));
      refreshSdkList();
      final Sdk currentSdk = getSdk();
      if (currentSdk != null) {
        mySdkList.setSelectedValue(currentSdk, true);
      }
    }
  }

  private void reloadSdk() {
    final Sdk currentSdk = getEditableSelectedSdk();
    if (currentSdk != null) {
      reloadSdk(currentSdk);
    }
  }

  private void reloadSdk(@NotNull Sdk currentSdk) {
    PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(currentSdk, myProject);
  }

  private void commitAndRefresh(@NotNull SdkModificator modificator) {
    modificator.commitChanges();
    myModified = true;
    refreshSdkList();
  }

  private class ToggleVirtualEnvFilterButton extends ToggleActionButton implements DumbAware {
    ToggleVirtualEnvFilterButton() {
      super(PyBundle.messagePointer("sdk.details.dialog.hide.all.virtual.envs"), AllIcons.General.Filter);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myHideOtherProjectVirtualenvs;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myHideOtherProjectVirtualenvs = state;
      refreshSdkList();
      updateOkButton();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  private class ShowPathButton extends DumbAwareAction {
    ShowPathButton() {
      super(PyBundle.messagePointer("sdk.details.dialog.show.interpreter.paths"), AllIcons.Actions.ShowAsTree);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(getEditableSelectedSdk() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Sdk sdk = getEditableSelectedSdk();
      if (sdk == null) return;
      final PythonPathEditor pathEditor = createPathEditor(sdk);
      final SdkModificator sdkModificator = sdk.getSdkModificator();

      PythonPathDialog dialog = new PythonPathDialog(myProject, pathEditor);
      pathEditor.reset(sdkModificator);
      if (dialog.showAndGet()) {
        if (pathEditor.isModified()) {
          pathEditor.apply(sdkModificator);
          commitAndRefresh(sdkModificator);
          reloadSdk(sdk);
        }
      }
      updateOkButton();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private @NotNull PythonPathEditor createPathEditor(@NotNull final Sdk sdk) {
    PythonPathEditor pathEditor;
    if (PythonSdkUtil.isRemote(sdk)) {
      pathEditor = new PyRemotePathEditor(myProject, sdk);
    }
    else {
      pathEditor = new PythonPathEditor(PyBundle.message("python.sdk.configuration.tab.title"), OrderRootType.CLASSES,
                                        FileChooserDescriptorFactory.createAllButJarContentsDescriptor());
    }
    pathEditor.addReloadPathsActionCallback(this::reloadSdk);
    return pathEditor;
  }

  private class MySdkModelListener implements SdkModel.Listener {

    @Override
    public void sdkAdded(@NotNull Sdk sdk) {
      refreshSdkList();
    }

    @Override
    public void sdkChanged(@NotNull Sdk sdk, String previousName) {
      refreshSdkList();
    }
  }

  private class SdkAddedCallback implements Consumer<Sdk> {
    @Override
    public void accept(@Nullable Sdk sdk) {
      if (sdk != null && myProjectSdksModel.findSdk(sdk.getName()) == null) {
        myProjectSdksModel.addSdk(sdk);
        setSelectedSdk(sdk);
      }
    }
  }
}
