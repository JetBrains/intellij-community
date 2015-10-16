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
package com.jetbrains.python.sdk;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ListCellRendererWrapper;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PySdkListCellRenderer extends ListCellRendererWrapper<Object> {
  private final String myNullText;
  private final Map<Sdk, SdkModificator> mySdkModifiers;
  public static final String SEPARATOR = "separator";

  final Pattern PYTHON_PATTERN = Pattern.compile("(\\d\\.?\\d\\.?\\d?)[ ]*\\(([^\\(\\)]*)\\)|(\\d\\.?\\d\\.?\\d?)[ ]*([^\\(\\)]*)");
  private boolean isShortVersion;

  public PySdkListCellRenderer(boolean shortVersion) {
    isShortVersion = shortVersion;
    myNullText = "";
    mySdkModifiers = null;
  }

  public PySdkListCellRenderer(String nullText, @Nullable Map<Sdk, SdkModificator> sdkModifiers) {
    myNullText = nullText;
    mySdkModifiers = sdkModifiers;
  }

  @Override
  public void customize(JList list, Object item, int index, boolean selected, boolean hasFocus) {
    if (item instanceof Sdk) {
      Sdk sdk = (Sdk)item;
      final PythonSdkFlavor flavor = PythonSdkFlavor.getPlatformIndependentFlavor(sdk.getHomePath());
      final Icon icon = flavor != null ? flavor.getIcon() : ((SdkType)sdk.getSdkType()).getIcon();

      String name;
      if (mySdkModifiers != null && mySdkModifiers.containsKey(sdk)) {
        name = mySdkModifiers.get(sdk).getName();
      }
      else {
        name = sdk.getName();
      }
      if (name.startsWith("Remote")) {
        final String trimmedRemote = StringUtil.trim(name.substring("Remote".length()));
        if (!trimmedRemote.isEmpty())
          name = trimmedRemote;
      }
      final String flavorName = flavor == null ? "Python" : flavor.getName();
      if (name.startsWith(flavorName)) name = StringUtil.trim(name.substring(flavorName.length()));

      if (isShortVersion){
        name = shortenName(name);
      }

      if (PythonSdkType.isInvalid(sdk)) {
        setText("[invalid] " + name);
        setIcon(wrapIconWithWarningDecorator(icon));
      }
      else if (PythonSdkType.isIncompleteRemote(sdk)) {
        setText("[incomplete] " + name);
        setIcon(wrapIconWithWarningDecorator(icon));
      }
      else if (PythonSdkType.hasInvalidRemoteCredentials(sdk)) {
        setText("[invalid] " + name);
        setIcon(wrapIconWithWarningDecorator(icon));
      }
      else if (sdk instanceof PyDetectedSdk) {
        setText(name);
        setIcon(IconLoader.getTransparentIcon(icon));
      }
      else {
        setText(name);
        setIcon(icon);
      }
    }
    else if (SEPARATOR.equals(item))
      setSeparator();
    else if (item == null)
      setText(myNullText);
  }

  private String shortenName(@NotNull String name) {
    final Matcher matcher = PYTHON_PATTERN.matcher(name);
    if (matcher.matches()) {
      String path = matcher.group(2);
      if (path != null) {
        name = matcher.group(1) + " at " + path;
      }
      else {
        path = matcher.group(4);
        final int index = path.lastIndexOf(File.separator);
        if (index > 0) {
          path = path.substring(index);
        }
        name = matcher.group(3) + " at ..." + path;
      }
    }
    else if (new File(name).exists()) {
      name = FileUtil.getLocationRelativeToUserHome(name);
    }
    return name;
  }

  private static LayeredIcon wrapIconWithWarningDecorator(Icon icon) {
    final LayeredIcon layered = new LayeredIcon(2);
    layered.setIcon(icon, 0);
    final Icon overlay = AllIcons.Actions.Cancel;
    layered.setIcon(overlay, 1);
    return layered;
  }
}
