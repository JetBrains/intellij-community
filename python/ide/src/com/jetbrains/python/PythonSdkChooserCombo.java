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
package com.jetbrains.python;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.util.Condition;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.configuration.PythonSdkConfigurable;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.PythonSdkType;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * @author yole
 */
public class PythonSdkChooserCombo extends ComboboxWithBrowseButton {
  private final List<ActionListener> myChangedListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  //public PythonSdkChooserCombo(final Condition<Sdk> acceptableSdkCondition) {
  //  this(PythonSdkType.getAllSdks(), acceptableSdkCondition);
  //}

  public PythonSdkChooserCombo(final Project project, List<Sdk> sdks, final Condition<Sdk> acceptableSdkCondition) {
    Sdk initialSelection = null;
    for (Sdk sdk : sdks) {
      if (acceptableSdkCondition.value(sdk)) {
        initialSelection = sdk;
        break;
      }
    }
    getComboBox().setModel(new CollectionComboBoxModel(sdks, initialSelection));
    getComboBox().setRenderer(new SdkListCellRenderer("<no interpreter>") {
      @Override
      protected Icon getSdkIcon(Sdk sdk) {
        final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
        return flavor != null ? flavor.getIcon() : ((SdkType)sdk.getSdkType()).getIcon();
      }
    });
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final PythonSdkConfigurable configurable = new PythonSdkConfigurable(project);
        configurable.setNewProject(true);
        ShowSettingsUtil.getInstance().editConfigurable(PythonSdkChooserCombo.this, configurable);
        Sdk selection = configurable.getRealSelectedSdk();
        final List<Sdk> sdks = PythonSdkType.getAllSdks();
        getComboBox().setModel(new CollectionComboBoxModel(sdks, selection));
        notifyChanged(e);
      }
    });
    getComboBox().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        notifyChanged(e);
      }
    });
  }

  private void notifyChanged(ActionEvent e) {
    for (ActionListener changedListener : myChangedListeners) {
      changedListener.actionPerformed(e);
    }
  }

  public void addChangedListener(ActionListener listener) {
    myChangedListeners.add(listener);
  }
}
