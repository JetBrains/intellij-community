// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.palette;

import com.intellij.designer.AbstractToolWindowManager;
import com.intellij.designer.DesignerCustomizations;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class PaletteToolWindowManager extends AbstractToolWindowManager {
  private PalettePanel myToolWindowPanel;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Public Access
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public PaletteToolWindowManager(Project project) {
    super(project);
  }

  public static PalettePanel getInstance(DesignerEditorPanel designer) {
    PaletteToolWindowManager manager = getInstance(designer.getProject());
    if (manager.isEditorMode()) {
      return (PalettePanel)manager.getContent(designer);
    }
    return manager.myToolWindowPanel;
  }

  public static PaletteToolWindowManager getInstance(Project project) {
    return project.getComponent(PaletteToolWindowManager.class);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Impl
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  protected void initToolWindow() {
    if (myToolWindowPanel == null) {
      myToolWindowPanel = new PalettePanel();
      Disposer.register(this, () -> myToolWindowPanel.dispose());
    }

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow("Palette\t", false, getAnchor(), myProject, true);
    myToolWindow.setIcon(AllIcons.Toolwindows.ToolWindowPalette);
    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content = contentManager.getFactory().createContent(myToolWindowPanel, null, false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    DesignerCustomizations customization = getCustomizations();
    return customization != null ? customization.getPaletteAnchor() : ToolWindowAnchor.RIGHT;
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myToolWindowPanel.loadPalette((DesignerEditorPanel)designer);

    if (designer == null) {
      myToolWindow.setAvailable(false, null);
    }
    else {
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "PaletteToolWindowManager";
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Impl
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    PalettePanel palettePanel = new PalettePanel();
    palettePanel.loadPalette((DesignerEditorPanel)designer);

    return createContent(designer,
                         palettePanel,
                         "Palette",
                         AllIcons.Toolwindows.ToolWindowPalette,
                         palettePanel,
                         palettePanel,
                         180,
                         null);
  }
}