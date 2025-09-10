// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public final class DesignerToolWindowManager extends AbstractToolWindowManager {
  private DesignerToolWindow myToolWindowContent;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Public Access
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public DesignerToolWindowManager(@NotNull Project project) {
    super(project);
  }

  public static DesignerToolWindow getInstance(DesignerEditorPanel designer) {
    DesignerToolWindowManager manager = getInstance(designer.getProject());
    if (manager.isEditorMode()) {
      return (DesignerToolWindow)manager.getContent(designer);
    }
    return manager.myToolWindowContent;
  }


  public static DesignerToolWindowManager getInstance(Project project) {
    return project.getComponent(DesignerToolWindowManager.class);
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Impl
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  protected void initToolWindow() {
    if (myToolWindowContent == null) {
      myToolWindowContent = new DesignerToolWindow(myProject, true);
      Disposer.register(this, () -> myToolWindowContent.dispose());
    }

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(DesignerBundle.message("designer.toolwindow.name"),
                                                                               false, getAnchor(), myProject, true);
    myToolWindow.setIcon(UiDesignerIcons.ToolWindowUIDesigner);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    myToolWindow.setTitleActions(myToolWindowContent.createActions());
    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content =
      contentManager.getFactory()
        .createContent(myToolWindowContent.getToolWindowPanel(), DesignerBundle.message("designer.toolwindow.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowContent.getComponentTree());
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false);
  }

  @Override
  protected ToolWindowAnchor getAnchor() {
    DesignerCustomizations customization = getCustomizations();
    return customization != null ? customization.getStructureAnchor() : ToolWindowAnchor.LEFT;
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myToolWindowContent.update((DesignerEditorPanel)designer);

    if (designer == null) {
      myToolWindow.setAvailable(false);
    }
    else {
      myToolWindow.setAvailable(true);
      myToolWindow.show(null);
    }
  }

  @Override
  public @NotNull String getComponentName() {
    return "UIDesignerToolWindowManager2";
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Impl
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    DesignerToolWindow toolWindowContent = new DesignerToolWindow(myProject, false);
    toolWindowContent.update((DesignerEditorPanel)designer);

    return createContent(designer,
                         toolWindowContent,
                         DesignerBundle.message("designer.toolwindow.title"),
                         UiDesignerIcons.ToolWindowUIDesigner,
                         toolWindowContent.getToolWindowPanel(),
                         toolWindowContent.getComponentTree(),
                         320,
                         toolWindowContent.createActions());
  }
}