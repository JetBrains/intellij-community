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

import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager extends LightToolWindowManager {
  protected AbstractToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(FileEditor editor) {
    if (editor instanceof DesignerEditor) {
      DesignerEditor designerEditor = (DesignerEditor)editor;
      return designerEditor.getDesignerPanel();
    }
    return null;
  }

  @Override
  protected ToggleEditorModeAction createToggleAction(ToolWindowAnchor anchor) {
    return new ToggleEditorModeAction(this, myProject, anchor) {
      @Override
      protected LightToolWindowManager getOppositeManager() {
        AbstractToolWindowManager designerManager = DesignerToolWindowManager.getInstance(myProject);
        AbstractToolWindowManager paletteManager = PaletteToolWindowManager.getInstance(myProject);
        return myManager == designerManager ? paletteManager : designerManager;
      }
    };
  }

  @Nullable
  protected static DesignerCustomizations getCustomizations() {
    return DesignerCustomizations.EP_NAME.findExtension(DesignerCustomizations.class);
  }
}