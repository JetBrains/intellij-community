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
package com.jetbrains.python.configuration;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.util.NullableConsumer;
import com.intellij.util.ui.JBUI;
import com.intellij.webcore.packaging.PackagesNotificationPanel;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.packaging.PyPackageManagers;
import com.jetbrains.python.packaging.ui.PyInstalledPackagesPanel;
import com.jetbrains.python.sdk.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.jetbrains.python.sdk.PySdkRenderingKt.groupModuleSdksByTypes;

public class PyActiveSdkConfigurable implements UnnamedConfigurable {

  private static final Logger LOG = Logger.getInstance(PyActiveSdkConfigurable.class);

  @NotNull
  private final Project myProject;

  @Nullable
  private final Module myModule;

  @NotNull
  private final PyConfigurableInterpreterList myInterpreterList;

  @NotNull
  private final ProjectSdksModel myProjectSdksModel;

  @NotNull
  private final JPanel myMainPanel;

  @NotNull
  private final ComboBox<Object> mySdkCombo;

  @NotNull
  private final PyInstalledPackagesPanel myPackagesPanel;

  @Nullable
  private final Disposable myDisposable;

  public PyActiveSdkConfigurable(@NotNull Project project) {
    this(project, null);
  }

  public PyActiveSdkConfigurable(@NotNull Module module) {
    this(module.getProject(), module);
  }

  private PyActiveSdkConfigurable(@NotNull Project project, @Nullable Module module) {
    myProject = project;
    myModule = module;

    mySdkCombo = buildSdkComboBox(this::onShowAllSelected, this::onSdkSelected);

    final PackagesNotificationPanel packagesNotificationPanel = new PackagesNotificationPanel();
    myPackagesPanel = new PyInstalledPackagesPanel(myProject, packagesNotificationPanel);

    final Pair<PyCustomSdkUiProvider, Disposable> customizer = buildCustomizer();
    myDisposable = customizer == null ? null : customizer.second;

    final JButton detailsButton = buildDetailsButton(mySdkCombo, this::onShowDetailsClicked);

    myMainPanel = buildPanel(project, mySdkCombo, detailsButton, myPackagesPanel, packagesNotificationPanel, customizer);

    myInterpreterList = PyConfigurableInterpreterList.getInstance(myProject);
    myProjectSdksModel = myInterpreterList.getModel();
  }

  @NotNull
  private static ComboBox<Object> buildSdkComboBox(@NotNull Runnable onShowAllSelected, @NotNull Runnable onSdkSelected) {
    final ComboBox<Object> result = new ComboBox<Object>() {
      @Override
      public void setSelectedItem(Object item) {
        if (getShowAll().equals(item)) {
          onShowAllSelected.run();
        }
        else if (!PySdkListCellRenderer.SEPARATOR.equals(item)) {
          super.setSelectedItem(item);
        }
      }
    };

    result.addItemListener(
      e -> {
        if (e.getStateChange() == ItemEvent.SELECTED) onSdkSelected.run();
      }
    );

    new ComboboxSpeedSearch(result);
    result.setPreferredSize(result.getPreferredSize()); // this line allows making `result` resizable
    return result;
  }

  @Nullable
  private static Pair<PyCustomSdkUiProvider, Disposable> buildCustomizer() {
    final PyCustomSdkUiProvider customUiProvider = PyCustomSdkUiProvider.getInstance();
    return customUiProvider == null ? null : new Pair<>(customUiProvider, Disposer.newDisposable());
  }

  @NotNull
  private static JButton buildDetailsButton(@NotNull ComboBox<?> sdkComboBox, @NotNull Consumer<JButton> onShowDetails) {
    final FixedSizeButton result = new FixedSizeButton(sdkComboBox.getPreferredSize().height);
    result.setIcon(AllIcons.General.GearPlain);
    result.addActionListener(e -> onShowDetails.accept(result));
    return result;
  }

  @NotNull
  private static JPanel buildPanel(@NotNull Project project,
                                   @NotNull ComboBox<?> sdkComboBox,
                                   @NotNull JButton detailsButton,
                                   @NotNull PyInstalledPackagesPanel installedPackagesPanel,
                                   @NotNull PackagesNotificationPanel packagesNotificationPanel,
                                   @Nullable Pair<PyCustomSdkUiProvider, Disposable> customizer) {
    final JPanel result = new JPanel(new GridBagLayout());

    final GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.HORIZONTAL;
    c.insets = JBUI.insets(2);

    c.gridx = 0;
    c.gridy = 0;
    JLabel label = new JLabel(PyBundle.message("active.sdk.dialog.project.interpreter"));
    label.setLabelFor(sdkComboBox);
    result.add(label, c);

    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 0.1;
    result.add(sdkComboBox, c);

    c.insets = JBUI.insets(2, 0, 2, 2);
    c.gridx = 2;
    c.gridy = 0;
    c.weightx = 0.0;
    result.add(detailsButton, c);

    if (customizer != null) {
      customizer.first.customizeActiveSdkPanel(project, sdkComboBox, result, c, customizer.second);
    }

    c.insets = JBUI.insets(2, 2, 0, 2);
    c.gridx = 0;
    c.gridy++;
    c.gridwidth = 3;
    c.weightx = 0.0;
    result.add(new JLabel("  "), c);

    c.gridx = 0;
    c.gridy++;
    c.weighty = 1.;
    c.gridwidth = 3;
    c.gridheight = GridBagConstraints.RELATIVE;
    c.fill = GridBagConstraints.BOTH;
    result.add(installedPackagesPanel, c);

    c.gridheight = GridBagConstraints.REMAINDER;
    c.gridx = 0;
    c.gridy++;
    c.gridwidth = 3;
    c.weighty = 0.;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.SOUTH;

    result.add(packagesNotificationPanel.getComponent(), c);

    return result;
  }

  private void onShowAllSelected() {
    buildAllSdksDialog().show();
  }

  private void onSdkSelected() {
    final Sdk sdk = getOriginalSelectedSdk();
    final PyPackageManagers packageManagers = PyPackageManagers.getInstance();
    myPackagesPanel.updatePackages(sdk != null ? packageManagers.getManagementService(myProject, sdk) : null);
    myPackagesPanel.updateNotifications(sdk);
  }

  private void onShowDetailsClicked(@NotNull JButton detailsButton) {
    PythonSdkDetailsStep.show(myProject, myModule, myProjectSdksModel.getSdks(), buildAllSdksDialog(), myMainPanel,
                              detailsButton.getLocationOnScreen(), new SdkAddedCallback());
  }

  @NotNull
  private PythonSdkDetailsDialog buildAllSdksDialog() {
    return new PythonSdkDetailsDialog(
      myProject,
      myModule,
      selectedSdk -> {
        if (selectedSdk != null) {
          updateSdkListAndSelect(selectedSdk);
        }
        else {
          // do not use `getOriginalSelectedSdk()` here since `model` won't find original sdk for selected item due to applying
          final Sdk currentSelectedSdk = getEditableSelectedSdk();

          if (currentSelectedSdk != null && myProjectSdksModel.findSdk(currentSelectedSdk.getName()) != null) {
            // nothing has been selected but previously selected sdk still exists, stay with it
            updateSdkListAndSelect(currentSelectedSdk);
          }
          else {
            // nothing has been selected but previously selected sdk removed, switch to `No interpreter`
            updateSdkListAndSelect(null);
          }
        }
      },
      reset -> {
        // data is invalidated on `model` resetting so we need to reload sdks to not stuck with outdated ones
        // do not use `getOriginalSelectedSdk()` here since `model` won't find original sdk for selected item due to resetting
        if (reset) updateSdkListAndSelect(getEditableSelectedSdk());
      }
    );
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    return !Comparing.equal(getSdk(), getOriginalSelectedSdk());
  }

  @Nullable
  private Sdk getOriginalSelectedSdk() {
    final Sdk editableSdk = getEditableSelectedSdk();
    return editableSdk == null ? null : myProjectSdksModel.findSdk(editableSdk);
  }

  @Nullable
  private Sdk getEditableSelectedSdk() {
    return (Sdk)mySdkCombo.getSelectedItem();
  }

  @Nullable
  protected Sdk getSdk() {
    if (myModule == null) {
      return ProjectRootManager.getInstance(myProject).getProjectSdk();
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
    return rootManager.getSdk();
  }

  @Override
  public void apply() {
    final Sdk selectedSdk = getOriginalSelectedSdk();
    if (selectedSdk != null) {
      ((PythonSdkType)selectedSdk.getSdkType()).setupSdkPaths(selectedSdk);
    }
    setSdk(selectedSdk);
  }

  protected void setSdk(@Nullable Sdk item) {
    PySdkExtKt.setPythonSdk(myProject, item);
    if (myModule != null) {
      PySdkExtKt.setPythonSdk(myModule, item);
    }
  }

  @Override
  public void reset() {
    updateSdkListAndSelect(getSdk());
  }

  private void updateSdkListAndSelect(@Nullable Sdk selectedSdk) {
    final List<Sdk> allPythonSdks = myInterpreterList.getAllPythonSdks(myProject);

    final List<Object> items = new ArrayList<>();
    items.add(null);

    final Map<PyRenderedSdkType, List<Sdk>> moduleSdksByTypes = groupModuleSdksByTypes(allPythonSdks, myModule, PythonSdkUtil::isInvalid);

    final PyRenderedSdkType[] renderedSdkTypes = PyRenderedSdkType.values();
    for (int i = 0; i < renderedSdkTypes.length; i++) {
      final PyRenderedSdkType currentSdkType = renderedSdkTypes[i];

      if (moduleSdksByTypes.containsKey(currentSdkType)) {
        if (i != 0) items.add(PySdkListCellRenderer.SEPARATOR);
        items.addAll(moduleSdksByTypes.get(currentSdkType));
      }
    }

    items.add(PySdkListCellRenderer.SEPARATOR);
    items.add(getShowAll());

    mySdkCombo.setRenderer(new PySdkListCellRenderer(null));
    final Sdk selection = selectedSdk == null ? null : myProjectSdksModel.findSdk(selectedSdk.getName());
    mySdkCombo.setModel(new CollectionComboBoxModel<>(items, selection));
    // The call of `setSelectedItem` is required to notify `PyPathMappingsUiProvider` about initial setting of `Sdk` via `setModel` above
    // Fragile as it is vulnerable to changes of `setSelectedItem` method in respect to processing `ActionEvent`
    mySdkCombo.setSelectedItem(selection);
    onSdkSelected();
  }

  @Override
  public void disposeUIResources() {
    myInterpreterList.disposeModel();
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
    }
  }

  private class SdkAddedCallback implements NullableConsumer<Sdk> {
    @Override
    public void consume(Sdk sdk) {
      if (sdk != null && myProjectSdksModel.findSdk(sdk.getName()) == null) {
        myProjectSdksModel.addSdk(sdk);
        try {
          myProjectSdksModel.apply(null, true);
        }
        catch (ConfigurationException e) {
          LOG.error(e);
        }
        updateSdkListAndSelect(sdk);
      }
    }
  }

  private static String getShowAll() {
    return PyBundle.message("active.sdk.dialog.show.all.item");
  }
}
