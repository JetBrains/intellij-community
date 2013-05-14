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

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.SideBorder;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import icons.UIDesignerNewIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public final class DesignerToolWindowManager extends AbstractToolWindowManager {
  private final DesignerToolWindow myToolWindowContent;

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Public Access
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public DesignerToolWindowManager(Project project, FileEditorManager fileEditorManager) {
    super(project, fileEditorManager, SideBorder.LEFT);
    myToolWindowContent = new DesignerToolWindow(project, true);
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
  protected LightToolWindow createContent(DesignerEditorPanel designer) {
    DesignerToolWindow toolWindowContent = new DesignerToolWindow(myProject, false);
    toolWindowContent.update(designer);

    String title = DesignerBundle.message("designer.toolwindow.title");

    return createContent(designer,
                         toolWindowContent,
                         title,
                         title,
                         UIDesignerNewIcons.ToolWindow,
                         toolWindowContent.getToolWindowPanel(),
                         toolWindowContent.getComponentTree(),
                         320,
                         toolWindowContent.createActions());
  }

  @Override
  protected void updateToolWindow(@Nullable DesignerEditorPanel designer) {
    myToolWindowContent.update(designer);

    if (designer == null) {
      myToolWindow.setAvailable(false, null);
    }
    else {
      myToolWindow.setAvailable(true, null);
      myToolWindow.show(null);
    }
  }

  @Override
  protected void initToolWindow() {
    DesignerCustomizations customization = getCustomizations();
    ToolWindowAnchor anchor = customization != null ? customization.getStructureAnchor() : ToolWindowAnchor.LEFT;

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(DesignerBundle.message("designer.toolwindow.name"),
                                                                               false, anchor, myProject, true);
    myToolWindow.setIcon(UIDesignerNewIcons.ToolWindow);
    myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");

    ((ToolWindowEx)myToolWindow).setTitleActions(myToolWindowContent.createActions());
    initGearActions();

    ContentManager contentManager = myToolWindow.getContentManager();
    Content content =
      contentManager.getFactory()
        .createContent(myToolWindowContent.getToolWindowPanel(), DesignerBundle.message("designer.toolwindow.title"), false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(myToolWindowContent.getComponentTree());
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    myToolWindow.setAvailable(false, null);
  }

  @Override
  public void disposeComponent() {
    myToolWindowContent.dispose();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UIDesignerToolWindowManager2";
  }
}