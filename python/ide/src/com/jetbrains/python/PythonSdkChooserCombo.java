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
package com.jetbrains.python;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.configuration.PyConfigurableInterpreterList;
import com.jetbrains.python.sdk.PySdkListCellRenderer;
import com.jetbrains.python.sdk.PySdkService;
import com.jetbrains.python.sdk.PythonSdkDetailsStep;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author yole
 */
public class PythonSdkChooserCombo extends ComboboxWithBrowseButton {
  private final List<ActionListener> myChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PythonSdkChooserCombo");

  @SuppressWarnings("unchecked")
  public PythonSdkChooserCombo(final Project project, List<Sdk> sdks, final Condition<Sdk> acceptableSdkCondition) {
    Sdk initialSelection = null;
    for (Sdk sdk : sdks) {
      if (acceptableSdkCondition.value(sdk)) {
        initialSelection = sdk;
        break;
      }
    }
    final JComboBox comboBox = getComboBox();
    comboBox.setModel(new CollectionComboBoxModel(sdks, initialSelection));
    comboBox.setRenderer(new PySdkListCellRenderer(true));
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        showOptions(project);
        notifyChanged(e);
      }
    });
    comboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        notifyChanged(e);
        updateTooltip();
      }
    });
    updateTooltip();
  }

  private void updateTooltip() {
    final Object item = getComboBox().getSelectedItem();
    getComboBox().setToolTipText(item instanceof Sdk ? ((Sdk)item).getHomePath() : null);
  }

  private void showOptions(final Project project) {
    final PyConfigurableInterpreterList interpreterList = PyConfigurableInterpreterList.getInstance(project);
    final Sdk[] sdks = interpreterList.getModel().getSdks();
    PythonSdkDetailsStep.show(project, sdks, null, this, getButton().getLocationOnScreen(), new NullableConsumer<Sdk>() {
      @Override
      public void consume(@Nullable Sdk sdk) {
        if (sdk == null) return;
        final PySdkService sdkService = PySdkService.getInstance();
        sdkService.restoreSdk(sdk);

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
        //noinspection unchecked
        getComboBox().setModel(new CollectionComboBoxModel(interpreterList.getAllPythonSdks(), sdk));
      }
    }, true);
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
}
