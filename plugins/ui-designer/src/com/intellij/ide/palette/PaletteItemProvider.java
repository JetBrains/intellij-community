// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.palette;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;

import java.beans.PropertyChangeListener;


public interface PaletteItemProvider {
  ExtensionPointName<PaletteItemProvider> EP_NAME = ExtensionPointName.create("com.intellij.paletteItemProvider");

  PaletteGroup[] getActiveGroups(VirtualFile virtualFile);

  void addListener(PropertyChangeListener listener);
  void removeListener(PropertyChangeListener listener);
}
