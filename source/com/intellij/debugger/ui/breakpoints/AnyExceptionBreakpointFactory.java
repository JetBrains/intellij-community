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
public class AnyExceptionBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new AnyExceptionBreakpoint(project);
  }

  public String getBreakpointCategory() {
    return AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT;
  }

  public String getComponentName() {
    return "AnyExceptionBreakpointFactory";
  }
}
