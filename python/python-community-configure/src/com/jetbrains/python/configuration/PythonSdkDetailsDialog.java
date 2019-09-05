// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.RemoteSdkAdditionalData;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.NullableConsumer;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PyRemoteSourceItem;
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
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

public class PythonSdkDetailsDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance(PythonSdkDetailsDialog.class);

  @NotNull
  private final Map<Sdk, SdkModificator> myModificators = FactoryMap.create(sdk -> sdk.getSdkModificator());

  private final Set<SdkModificator> myModifiedModificators = new HashSet<>();

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

    mySdkList = buildSdkList(myModificators, e -> updateOkButton());
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
  private static JBList<Sdk> buildSdkList(@NotNull Map<Sdk, SdkModificator> modificators,
                                          @NotNull ListSelectionListener selectionListener) {
    final JBList<Sdk> result = new JBList<>();
    result.setCellRenderer(new PySdkListCellRenderer(modificators));
    result.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    result.addListSelectionListener(selectionListener);
    new ListSpeedSearch<>(result);
    return result;
  }

  @NotNull
  private static JPanel buildPanel(@NotNull JBList<Sdk> sdkList,
                                   @NotNull AnActionButtonRunnable addAction,
                                   @NotNull AnActionButtonRunnable editAction,
                                   @NotNull AnActionButtonRunnable removeAction,
                                   @NotNull AnActionButton... extraActions) {
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
    super.setOKActionEnabled(myProjectSdksModel.isModified() || !myModifiedModificators.isEmpty() || getOriginalSelectedSdk() != getSdk());
  }

  @Override
  protected void doOKAction() {
    apply();
    super.doOKAction();
  }

  @Override
  public void doCancelAction() {
    myModificators.clear();
    myModifiedModificators.clear();
    final boolean modified = myProjectSdksModel.isModified();
    if (modified) {
      myProjectSdksModel.reset(myProject);
    }
    myCancelCallback.accept(modified);
    super.doCancelAction();
  }

  private void apply() {
    for (SdkModificator modificator : myModifiedModificators) {
      /* This should always be true barring bug elsewhere, log error on else? */
      if (modificator.isWritable()) {
        modificator.commitChanges();
      }
    }
    myModificators.clear();
    myModifiedModificators.clear();
    try {
      myProjectSdksModel.apply();
    }
    catch (ConfigurationException e) {
      LOG.error(e);
    }
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
    return mySdkList.getSelectedValue();
  }

  private void refreshSdkList() {
    final List<Sdk> allPythonSdks = myInterpreterList.getAllPythonSdks(myProject);
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
    final EditSdkDialog dialog = new EditSdkDialog(myProject, modificator, s -> {
      if (isDuplicateSdkName(s, currentSdk)) {
        return PyBundle.message("sdk.details.dialog.error.duplicate.name");
      }
      return null;
    });
    if (dialog.showAndGet()) {
      mySdkList.repaint();
      final boolean pathChanged = !Comparing.equal(currentSdk.getHomePath(), dialog.getHomePath());
      if (!modificator.getName().equals(dialog.getName()) || pathChanged || dialog.isAssociateChanged()) {
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
    final Sdk selectedSdk = getEditableSelectedSdk();
    if (selectedSdk != null) {
      myProjectSdksModel.removeSdk(selectedSdk);

      if (myModificators.containsKey(selectedSdk)) {
        SdkModificator modificator = myModificators.get(selectedSdk);
        myModifiedModificators.remove(modificator);
        myModificators.remove(selectedSdk);
      }
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
      myModifiedModificators.add(myModificators.get(currentSdk));
      reloadSdk(currentSdk);
    }
  }

  private void reloadSdk(@NotNull Sdk currentSdk) {
    /* PythonSdkUpdater.update invalidates the modificator so we need to create a new
      one for further changes
     */
    if (PythonSdkUpdater.update(currentSdk, myModificators.get(currentSdk), myProject, null)){
      myModifiedModificators.remove(myModificators.get(currentSdk));
      myModificators.put(currentSdk, currentSdk.getSdkModificator());
    }
  }

  private class ToggleVirtualEnvFilterButton extends ToggleActionButton implements DumbAware {
    ToggleVirtualEnvFilterButton() {
      super(PyBundle.message("sdk.details.dialog.hide.all.virtual.envs"), AllIcons.General.Filter);
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
  }

  private class ShowPathButton extends AnActionButton implements DumbAware {
    ShowPathButton() {
      super(PyBundle.message("sdk.details.dialog.show.interpreter.paths"), AllIcons.Actions.ShowAsTree);
    }

    @Override
    public boolean isEnabled() {
      return getEditableSelectedSdk() != null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Sdk sdk = getEditableSelectedSdk();
      final PythonPathEditor pathEditor = createPathEditor(sdk);
      final SdkModificator sdkModificator = myModificators.get(sdk);

      PythonPathDialog dialog = new PythonPathDialog(myProject, pathEditor);
      pathEditor.reset(sdk != null ? sdkModificator : null);
      if (dialog.showAndGet()) {
        if (pathEditor.isModified()) {
          pathEditor.apply(sdkModificator);
          myModifiedModificators.add(sdkModificator);
        }
      }
      updateOkButton();
    }
  }

  private PythonPathEditor createPathEditor(final Sdk sdk) {
    if (PythonSdkUtil.isRemote(sdk)) {
      return new PyRemotePathEditor(sdk);
    }
    else {
      return new PythonPathEditor("Classes", OrderRootType.CLASSES, FileChooserDescriptorFactory.createAllButJarContentsDescriptor()) {
        @Override
        protected void onReloadButtonClicked() {
          reloadSdk();
        }
      };
    }
  }

  private class PyRemotePathEditor extends PythonPathEditor {
    private final PyRemoteSdkAdditionalDataBase myRemoteSdkData;
    private final Sdk mySdk;

    private final List<PathMappingSettings.PathMapping> myNewMappings = Lists.newArrayList();

    PyRemotePathEditor(Sdk sdk) {
      super("Classes", OrderRootType.CLASSES, FileChooserDescriptorFactory.createAllButJarContentsDescriptor());
      mySdk = sdk;
      myRemoteSdkData = (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData();
    }

    @Override
    protected void onReloadButtonClicked() {
      reloadSdk();
    }

    @Override
    protected String getPresentablePath(VirtualFile value) {
      String path = value.getPath();
      return myRemoteSdkData.getPathMappings().convertToRemote(path);
    }

    @Override
    protected void addToolbarButtons(ToolbarDecorator toolbarDecorator) {
      toolbarDecorator.setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
          final VirtualFile[] added = doAddItems();
          if (added.length > 0) {
            setModified(true);
          }
          requestDefaultFocus();
          setSelectedRoots(added);
        }
      });

      super.addToolbarButtons(toolbarDecorator);
    }

    @Override
    protected VirtualFile[] doAddItems() {
      Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(myPanel));
      try {
        String[] files = PythonRemoteInterpreterManager
          .getInstance().chooseRemoteFiles(project, (PyRemoteSdkAdditionalDataBase)mySdk.getSdkAdditionalData(), false);

        final String sourcesLocalPath = PythonSdkUtil.getRemoteSourcesLocalPath(mySdk.getHomePath());

        VirtualFile[] vFiles = new VirtualFile[files.length];

        int i = 0;
        for (String file : files) {
          String localRoot = PyRemoteSourceItem.localPathForRemoteRoot(sourcesLocalPath, file);

          myNewMappings.add(new PathMappingSettings.PathMapping(localRoot, file));
          myRemoteSdkData.getPathMappings().addMappingCheckUnique(localRoot, file);

          if (!new File(localRoot).exists()) {
            new File(localRoot).mkdirs();
          }
          vFiles[i++] = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(localRoot));
        }

        vFiles = adjustAddedFileSet(myPanel, vFiles);
        List<VirtualFile> added = new ArrayList<>(vFiles.length);
        for (VirtualFile vFile : vFiles) {
          if (addElement(vFile)) {
            added.add(vFile);
          }
        }
        return VfsUtilCore.toVirtualFileArray(added);
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return VirtualFile.EMPTY_ARRAY;
    }

    @Override
    public void apply(SdkModificator sdkModificator) {
      if (sdkModificator.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataBase) {
        for (PathMappingSettings.PathMapping mapping : myNewMappings) {
          ((PyRemoteSdkAdditionalDataBase)sdkModificator.getSdkAdditionalData()).getPathMappings()
            .addMappingCheckUnique(mapping.getLocalRoot(), mapping.getRemoteRoot());
        }
      }
      super.apply(sdkModificator);
    }
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
