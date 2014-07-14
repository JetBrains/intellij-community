/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
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

  @NotNull
  protected abstract DesignerEditorPanel createDesignerPanel(Project project, Module module, VirtualFile file);

  public final DesignerEditorPanel getDesignerPanel() {
    return myDesignerPanel;
  }

  @NotNull
  @Override
  public final JComponent getComponent() {
    return myDesignerPanel;
  }

  @Override
  public final JComponent getPreferredFocusedComponent() {
    return myDesignerPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "Design";
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
  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
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

  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }
}