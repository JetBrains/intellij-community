// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.palette.impl;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.uiDesigner.AbstractToolWindowManager;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class PaletteToolWindowManager extends AbstractToolWindowManager {
  private PaletteWindow myToolWindowPanel;

  public PaletteToolWindowManager(Project project) {
    super(project);
  }

  public static PaletteWindow getInstance(GuiEditor designer) {
    PaletteToolWindowManager manager = getInstance(designer.getProject());
    if (manager.isEditorMode()) {
      return (PaletteWindow)manager.getContent(designer);
    }
    if (manager.myToolWindowPanel == null) {
      manager.initToolWindow();
    }
    return manager.myToolWindowPanel;
  }

  public static PaletteToolWindowManager getInstance(Project project) {
    return project.getService(PaletteToolWindowManager.class);
  }

  @Override
  protected void initToolWindow() {
    myToolWindowPanel = new PaletteWindow(myProject);
    Disposer.register(this, () -> myToolWindowPanel.dispose());

    myToolWindow = ToolWindowManager.getInstance(myProject)
      .registerToolWindow(IdeBundle.message("toolwindow.palette"), false, getAnchor(), this, true);
    myToolWindow.setIcon(AllIcons.Toolwindows.ToolWindowPalette);
    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(myToolWindowPanel, null, false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myToolWindowPanel.refreshPaletteIfChanged((GuiEditor)designer);

    if (designer == null) {
      myToolWindow.setAvailable(false);
    }
    else {
      myToolWindow.setAvailable(true);
      myToolWindow.show(null);
    }
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    return ToolWindowAnchor.RIGHT;
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    PaletteWindow palettePanel = new PaletteWindow(myProject);
    palettePanel.refreshPaletteIfChanged((GuiEditor)designer);

    return createContent(designer,
                         palettePanel,
                         IdeBundle.message("toolwindow.palette"),
                         AllIcons.Toolwindows.ToolWindowPalette,
                         palettePanel,
                         palettePanel,
                         180,
                         null);
  }

  @Override
  public @NotNull String getComponentName() {
    return "PaletteManager";
  }
}