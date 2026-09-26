// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindowManager;
import com.intellij.designer.ToggleEditorModeAction;
import com.intellij.ide.palette.impl.PaletteToolWindowManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.uiDesigner.editor.UIFormEditor;
import com.intellij.uiDesigner.propertyInspector.DesignerToolWindowManager;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager extends LightToolWindowManager {
  protected AbstractToolWindowManager(Project project) {
    super(project);
  }

  @Override
  protected @Nullable DesignerEditorPanelFacade getDesigner(FileEditor editor) {
    if (editor instanceof UIFormEditor formEditor) {
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