/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class LineBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new LineBreakpoint(project);
  }

  public BreakpointPanel createBreakpointPanel(Project project) {
    return null;
  }
  public String getBreakpointCategory() {
    return LineBreakpoint.CATEGORY;
  }

  public String getComponentName() {
    return "LineBreakpointFactory";
  }
}
