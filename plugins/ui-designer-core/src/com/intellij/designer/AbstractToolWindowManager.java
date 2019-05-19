// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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