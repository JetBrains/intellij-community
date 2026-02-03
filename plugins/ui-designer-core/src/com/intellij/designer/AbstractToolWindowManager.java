// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer;

import com.intellij.designer.palette.PaletteToolWindowManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractToolWindowManager extends LightToolWindowManager {
  protected AbstractToolWindowManager(@NotNull Project project) {
    super(project);
  }

  @Override
  protected @Nullable DesignerEditorPanelFacade getDesigner(FileEditor editor) {
    if (editor instanceof DesignerEditor designerEditor) {
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

  protected static @Nullable DesignerCustomizations getCustomizations() {
    return DesignerCustomizations.EP_NAME.findExtension(DesignerCustomizations.class);
  }
}