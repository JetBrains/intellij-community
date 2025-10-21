// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.uiDesigner.AbstractToolWindowManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import icons.UIDesignerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class DesignerToolWindowManager extends AbstractToolWindowManager implements Disposable {
  private DesignerToolWindow myToolWindowPanel;

  public DesignerToolWindowManager(@NotNull Project project) {
    super(project);
  }

  public static DesignerToolWindow getInstance(@NotNull GuiEditor designer) {
    DesignerToolWindowManager manager = getInstance(designer.getProject());
    if (manager.isEditorMode()) {
      return (DesignerToolWindow)manager.getContent(designer);
    }
    if (manager.myToolWindowPanel == null) {
      manager.initToolWindow();
    }
    return manager.myToolWindowPanel;
  }

  public static DesignerToolWindowManager getInstance(@NotNull Project project) {
    return project.getService(DesignerToolWindowManager.class);
  }

  public @Nullable GuiEditor getActiveFormEditor() {
    return (GuiEditor)getActiveDesigner();
  }

  @Override
  protected void initToolWindow() {
    myToolWindowPanel = new DesignerToolWindow(myProject);
    Disposer.register(this, () -> myToolWindowPanel.dispose());

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(UIDesignerBundle.message("toolwindow.ui.designer.name"),
                                                                               false, getAnchor(), this, true);
    myToolWindow.setIcon(UIDesignerIcons.ToolWindowUIDesigner);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content = contentManager.getFactory()
      .createContent(myToolWindowPanel.getToolWindowPanel(), UIDesignerBundle.message("toolwindow.ui.designer.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowPanel.getComponentTree());
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myToolWindowPanel.update((GuiEditor)designer);

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
    return ToolWindowAnchor.LEFT;
  }

  @Override
  protected LightToolWindow createContent(@NotNull DesignerEditorPanelFacade designer) {
    DesignerToolWindow toolWindowContent = new DesignerToolWindow(myProject);
    toolWindowContent.update((GuiEditor)designer);

    return createContent(designer,
                         toolWindowContent,
                         UIDesignerBundle.message("toolwindow.ui.designer.title"),
                         UIDesignerIcons.ToolWindowUIDesigner,
                         toolWindowContent.getToolWindowPanel(),
                         toolWindowContent.getComponentTree(),
                         320,
                         null);
  }

  @Override
  public @NotNull String getComponentName() {
    return "UIDesignerToolWindowManager";
  }
}