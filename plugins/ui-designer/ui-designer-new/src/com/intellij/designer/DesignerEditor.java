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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author Alexander Lobas
 */
public abstract class DesignerEditor extends UserDataHolderBase implements FileEditor {
  private final DesignerEditorPanel myDesignerPanel;

  public DesignerEditor(Project project, VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      file = ((LightVirtualFile)file).getOriginalFile();
    }
    Module module = ModuleUtil.findModuleForFile(file, project);
    if (module == null) {
      throw new IllegalArgumentException("No module for file " + file + " in project " + project);
    }
    myDesignerPanel = new DesignerEditorPanel(module, file);
  }

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

  @Override
  public void dispose() {
    myDesignerPanel.dispose();
  }

  @Override
  public void selectNotify() {
  }

  @Override
  public void deselectNotify() {
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
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