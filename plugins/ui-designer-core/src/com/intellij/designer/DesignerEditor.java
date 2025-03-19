// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditor extends UserDataHolderBase implements FileEditor {
  protected final DesignerEditorPanel myDesignerPanel;

  public DesignerEditor(Project project, VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      file = ((LightVirtualFile)file).getOriginalFile();
    }
    myDesignerPanel = createDesignerPanel(project, findModule(project, file), file);
  }

  protected abstract Module findModule(Project project, VirtualFile file);

  protected abstract @NotNull DesignerEditorPanel createDesignerPanel(Project project, Module module, VirtualFile file);

  public final DesignerEditorPanel getDesignerPanel() {
    return myDesignerPanel;
  }

  @Override
  public final @NotNull JComponent getComponent() {
    return myDesignerPanel;
  }

  @Override
  public final JComponent getPreferredFocusedComponent() {
    return myDesignerPanel.getPreferredFocusedComponent();
  }

  @Override
  public @NotNull String getName() {
    return DesignerBundle.message("design");
  }

  @Override
  public void dispose() {
    myDesignerPanel.dispose();
  }

  @Override
  public void selectNotify() {
    myDesignerPanel.activate();
  }

  @Override
  public void deselectNotify() {
    myDesignerPanel.deactivate();
  }

  @Override
  public boolean isValid() {
    return myDesignerPanel.isEditorValid();
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return myDesignerPanel.createState();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }
}