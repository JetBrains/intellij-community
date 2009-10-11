/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * @author yole
 */
public class UIDesignerPaletteProvider implements PaletteItemProvider {
  private final Palette myPalette;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  @NonNls private static final String PROPERTY_GROUPS = "groups";

  public UIDesignerPaletteProvider(final Palette palette) {
    myPalette = palette;
    myPalette.addListener(new Palette.Listener() {
      public void groupsChanged(Palette palette) {
        fireGroupsChanged();
      }
    });

  }

  void fireGroupsChanged() {
    myPropertyChangeSupport.firePropertyChange(PROPERTY_GROUPS, null, null);
  }

  public PaletteGroup[] getActiveGroups(VirtualFile vFile) {
    if (vFile.getFileType().equals(StdFileTypes.GUI_DESIGNER_FORM)) {
      return myPalette.getToolWindowGroups();
    }
    return PaletteGroup.EMPTY_ARRAY;
  }

  public void addListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
}
