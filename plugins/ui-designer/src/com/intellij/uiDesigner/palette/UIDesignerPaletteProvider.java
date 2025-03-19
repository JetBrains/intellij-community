// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.uiDesigner.palette;

import com.intellij.ide.palette.PaletteGroup;
import com.intellij.ide.palette.PaletteItemProvider;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.uiDesigner.GuiFormFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;


public final class UIDesignerPaletteProvider implements PaletteItemProvider {
  private static final @NonNls String PROPERTY_GROUPS = "groups";

  private final Project myProject;
  private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);
  private Palette.Listener myListener;

  public UIDesignerPaletteProvider(final Project project) {
    myProject = project;
  }

  void fireGroupsChanged() {
    myPropertyChangeSupport.firePropertyChange(PROPERTY_GROUPS, null, null);
  }

  @Override
  public PaletteGroup[] getActiveGroups(VirtualFile vFile) {
    if (FileTypeRegistry.getInstance().isFileOfType(vFile, GuiFormFileType.INSTANCE)) {
      Palette palette = Palette.getInstance(myProject);
      if (myListener == null) {
        myListener = new Palette.Listener() {
          @Override
          public void groupsChanged(@NotNull Palette palette) {
            fireGroupsChanged();
          }
        };
        palette.addListener(myListener);
      }
      return palette.getToolWindowGroups();
    }
    return PaletteGroup.EMPTY_ARRAY;
  }

  @Override
  public void addListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removeListener(PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(listener);
  }
}
