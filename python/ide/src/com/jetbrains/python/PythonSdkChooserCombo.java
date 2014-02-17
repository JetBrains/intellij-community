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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.impl.SdkListCellRenderer;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.util.NullableConsumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.configuration.PythonSdkDetailsDialog;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
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
    comboBox.setRenderer(new SdkListCellRenderer("<no interpreter>") {
      @Override
      protected Icon getSdkIcon(Sdk sdk) {
        final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
        final Icon icon = flavor != null ? flavor.getIcon() : ((SdkType)sdk.getSdkType()).getIcon();
        return sdk instanceof PyDetectedSdk ? IconLoader.getTransparentIcon(icon) : icon;
      }
    });
    addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final List<Sdk> sdks = PythonSdkType.getAllSdks();
        PythonSdkDetailsDialog dialog = new PythonSdkDetailsDialog(project, new NullableConsumer<Sdk>() {
          @Override
          public void consume(@Nullable Sdk sdk) {
            comboBox.setModel(new CollectionComboBoxModel(sdks, sdk));
          }
        });
        dialog.show();
        notifyChanged(e);
      }
    });
    comboBox.addActionListener(new ActionListener() {
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

  @SuppressWarnings("UnusedDeclaration")
  public void addChangedListener(ActionListener listener) {
    myChangedListeners.add(listener);
  }
}
