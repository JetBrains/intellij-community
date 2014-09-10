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
package com.intellij.uiDesigner;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.designer.ToggleEditorModeAction;
import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager extends LightToolWindowManager {
  public AbstractToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
  }

  @Override
  public void projectOpened() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      super.projectOpened();
    }
  }

  @Nullable
  @Override
  protected DesignerEditorPanelFacade getDesigner(FileEditor editor) {
    if (editor instanceof UIFormEditor) {
      UIFormEditor formEditor = (UIFormEditor)editor;
      return formEditor.getEditor();
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
}