// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.jetbrains.python;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowManager;

import java.awt.*;

public class PyDocumentationManager extends DocumentationManager {

  public PyDocumentationManager(Project project, ActionManager manager, TargetElementUtil targetElementUtil) {
    super(project, manager, targetElementUtil);
  }

  @Override
  protected void setToolwindowDefaultState() {
    final Rectangle rectangle = WindowManager.getInstance().getIdeFrame(myProject).suggestChildFrameBounds();
    myToolWindow.setDefaultState(ToolWindowAnchor.RIGHT, ToolWindowType.DOCKED, new Rectangle(rectangle.width/4, rectangle.height));
    myToolWindow.setType(ToolWindowType.DOCKED, null);
    myToolWindow.setAutoHide(false);
  }
}
