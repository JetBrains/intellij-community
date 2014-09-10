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
package com.intellij.uiDesigner.propertyInspector;

import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightToolWindow;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
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
public class DesignerToolWindowManager extends AbstractToolWindowManager {
  private final DesignerToolWindow myToolWindowPanel;

  public DesignerToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    super(project, fileEditorManager);
    myToolWindowPanel = ApplicationManager.getApplication().isHeadlessEnvironment() ? null : new DesignerToolWindow(project);
  }

  public static DesignerToolWindow getInstance(GuiEditor designer) {
    DesignerToolWindowManager manager = getInstance(designer.getProject());
    if (manager.isEditorMode()) {
      return (DesignerToolWindow)manager.getContent(designer);
    }
    return manager.myToolWindowPanel;
  }

  public static DesignerToolWindowManager getInstance(Project project) {
    return project.getComponent(DesignerToolWindowManager.class);
  }

  @Nullable
  public GuiEditor getActiveFormEditor() {
    return (GuiEditor)getActiveDesigner();
  }

  @Override
  protected void initToolWindow() {
    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(UIDesignerBundle.message("toolwindow.ui.designer.name"),
                                                                               false, getAnchor(), myProject, true);
    myToolWindow.setIcon(UIDesignerIcons.ToolWindowUIDesigner);

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
    }

    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content =
      contentManager.getFactory()
        .createContent(myToolWindowPanel.getToolWindowPanel(), UIDesignerBundle.message("toolwindow.ui.designer.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowPanel.getComponentTree());
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanelFacade designer) {
    myToolWindowPanel.update((GuiEditor)designer);

    if (designer == null) {
      myToolWindow.setAvailable(false, null);
    }
    else {
      myToolWindow.setAvailable(true, null);
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
  public void disposeComponent() {
    if (myToolWindowPanel != null) {
      myToolWindowPanel.dispose();
    }
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UIDesignerToolWindowManager";
  }
}