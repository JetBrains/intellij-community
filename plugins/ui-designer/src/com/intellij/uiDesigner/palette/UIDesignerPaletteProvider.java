/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
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
