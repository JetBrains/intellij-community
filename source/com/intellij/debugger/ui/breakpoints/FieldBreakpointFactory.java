/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class FieldBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new FieldBreakpoint(project);
  }

  public String getBreakpointCategory() {
    return FieldBreakpoint.CATEGORY;
  }

  public String getComponentName() {
    return "FieldBreakpointFactory";
  }
}
