/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.vfs.VirtualFile;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

/**
 * @author yole
 */
public class UIDesignerPaletteProvider implements PaletteItemProvider {
  private Palette myPalette;
  private PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  public UIDesignerPaletteProvider(final Palette palette) {
    myPalette = palette;
    myPalette.addListener(new Palette.Listener() {
      public void groupsChanged(Palette palette) {
        myPropertyChangeSupport.firePropertyChange("groups", null, null);
      }
    });
  }

  public PaletteGroup[] getActiveGroups(VirtualFile vFile) {
    final ArrayList<GroupItem> groups = myPalette.getGroups();
    return groups.toArray(new PaletteGroup[groups.size()]);
  }

  public void addListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  public void removeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
}
