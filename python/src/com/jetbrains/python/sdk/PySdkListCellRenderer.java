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
package com.jetbrains.python.sdk;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ListCellRendererWrapper;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

public class PySdkListCellRenderer extends ListCellRendererWrapper<Sdk> {
  private final String myNullText;
  private final Map<Sdk, SdkModificator> mySdkModifiers;

  public PySdkListCellRenderer() {
    myNullText = "";
    mySdkModifiers = null;
  }

  public PySdkListCellRenderer(String nullText, @Nullable Map<Sdk, SdkModificator> sdkModifiers) {
    myNullText = nullText;
    mySdkModifiers = sdkModifiers;
  }

  @Override
  public void customize(JList list, Sdk sdk, int index, boolean selected, boolean hasFocus) {
    if (sdk != null) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getPlatformIndependentFlavor(sdk.getHomePath());
      final Icon icon = flavor != null ? flavor.getIcon() : ((SdkType)sdk.getSdkType()).getIcon();

      final String name;
      if (mySdkModifiers != null && mySdkModifiers.containsKey(sdk)) {
        name = mySdkModifiers.get(sdk).getName();
      }
      else {
        name = sdk.getName();
      }

      if (PythonSdkType.isInvalid(sdk)) {
        setText("[invalid] " + name);
        setIcon(wrapIconWithWarningDecorator(icon));
      }
      else if (PythonSdkType.isIncompleteRemote(sdk)) {
        setText("[incomplete] " + name);
        setIcon(wrapIconWithWarningDecorator(icon));
      }
      else {
        setText(name);
        setIcon(icon);
      }
    }
    else {
      setText(myNullText);
    }
  }

  private LayeredIcon wrapIconWithWarningDecorator(Icon icon) {
    final LayeredIcon layered = new LayeredIcon(2);
    layered.setIcon(icon, 0);
    // TODO: Create a separate invalid SDK overlay icon (DSGN-497)
    final Icon overlay = AllIcons.Actions.Cancel;
    layered.setIcon(overlay, 1);
    return layered;
  }
}
