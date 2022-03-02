// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.steps;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.List;


public class PythonSdkChooserCombo extends ComboboxWithBrowseButton {
  private final List<ActionListener> myChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final Logger LOG = Logger.getInstance(PythonSdkChooserCombo.class);
  /**
   * @deprecated the value of the field is not used anywhere internally
   */
  @Deprecated
  @Nullable private String myNewProjectPath;

  public PythonSdkChooserCombo(@Nullable final Project project,
                               @Nullable final Module module,
                               @NotNull List<? extends Sdk> sdks,
                               @NotNull final Condition<? super Sdk> acceptableSdkCondition) {
    this(project, module, sdks, null, acceptableSdkCondition);
  }

  /**
   * @deprecated the value of the field corresponding to {@code newProjectPath} is not used anywhere internally; use
   * {@link #PythonSdkChooserCombo(Project, Module, List, Condition)} instead.
   */
  @SuppressWarnings("unchecked")
  @Deprecated
  public PythonSdkChooserCombo(@Nullable final Project project,
                               @Nullable final Module module,
                               @NotNull List<? extends Sdk> sdks,
                               @Nullable String newProjectPath,
                               @NotNull final Condition<? super Sdk> acceptableSdkCondition) {
    super(new ComboBox<>());
    myNewProjectPath = newProjectPath;
    final Sdk initialSelection = ContainerUtil.find(sdks, acceptableSdkCondition);
    final JComboBox comboBox = getComboBox();
    comboBox.setModel(new CollectionComboBoxModel(sdks, initialSelection));
    comboBox.setRenderer(new PySdkListCellRenderer());
    addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        showOptions(project, module);
        notifyChanged(e);
      }
    });
    comboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        notifyChanged(e);
        updateTooltip();
      }
    });
    new ComboboxSpeedSearch(comboBox);
    updateTooltip();
  }

  private void updateTooltip() {
    final Object item = getComboBox().getSelectedItem();
    String sdkHomePath = item instanceof Sdk ? ((Sdk)item).getHomePath() : null;
    getComboBox().setToolTipText(sdkHomePath != null ? FileUtil.toSystemDependentName(sdkHomePath) : null);
  }

  private void showOptions(@Nullable final Project project, @Nullable Module module) {
    final PyConfigurableInterpreterList interpreterList = PyConfigurableInterpreterList.getInstance(project);
    final Sdk[] sdks = interpreterList.getModel().getSdks();
    //noinspection unchecked
    final JComboBox<Sdk> comboBox = getComboBox();
    final Sdk oldSelectedSdk = (Sdk)comboBox.getSelectedItem();
    PyAddSdkDialog.show(project, module, Arrays.asList(sdks), sdk -> {
      if (sdk == null) return;
      final ProjectSdksModel projectSdksModel = interpreterList.getModel();
      if (projectSdksModel.findSdk(sdk) == null) {
        projectSdksModel.addSdk(sdk);
        try {
          projectSdksModel.apply();
        }
        catch (ConfigurationException e) {
          LOG.error("Error adding new python interpreter " + e.getMessage());
        }
      }
      final List<Sdk> committedSdks = interpreterList.getAllPythonSdks();
      final Sdk copiedSdk = interpreterList.getModel().findSdk(sdk.getName());
      comboBox.setModel(new CollectionComboBoxModel<>(committedSdks, oldSelectedSdk));
      comboBox.setSelectedItem(copiedSdk);
      notifyChanged(null);
    });
  }

  private void notifyChanged(ActionEvent e) {
    for (ActionListener changedListener : myChangedListeners) {
      changedListener.actionPerformed(e);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public void addChangedListener(ActionListener listener) {
    myChangedListeners.add(listener);
  }

  /**
   * @deprecated the value of the corresponding field is not used anywhere internally
   */
  @Deprecated
  public void setNewProjectPath(@Nullable String newProjectPath) {
    myNewProjectPath = newProjectPath;
  }
}
