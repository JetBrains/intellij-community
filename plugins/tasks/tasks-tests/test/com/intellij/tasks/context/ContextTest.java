// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.tasks.context;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.tasks.TaskManagerTestCase;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl;
import org.intellij.plugins.xsltDebugger.XsltBreakpointType;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class ContextTest extends TaskManagerTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WorkingContextManager.getInstance(getProject()).enableUntil(getTestRootDisposable());
  }

  public void testSaveContext() {
    WorkingContextManager manager = getContextManager();

    manager.saveContext("first", "comment");
    manager.clearContext();
    manager.loadContext("first");

    manager.saveContext(myTaskManager.getActiveTask());
    manager.clearContext();
    manager.restoreContext(myTaskManager.getActiveTask());
  }

  public void testPack() throws Exception {
    ProjectEx project = (ProjectEx)getProject();
    String name = project.getName();
    WorkingContextManager contextManager = getContextManager();
    try {
      project.setProjectName("pack");
      contextManager.getContextFile().delete();

      for (int i = 0; i < 5; i++) {
        contextManager.saveContext("context" + i, null);
        Thread.sleep(2000);
      }
      List<ContextInfo> history = contextManager.getContextHistory();
      ContextInfo first = history.get(0);
      System.out.println(first.date);
      ContextInfo last = history.get(history.size() - 1);
      System.out.println(last.date);
      contextManager.pack(3, 1);
      history = contextManager.getContextHistory();
      assertEquals(3, history.size());
      System.out.println(history.get(0).date);
      assertEquals("/context2", history.get(0).name);
    }
    finally {
      project.setProjectName(name);
    }
  }

  public void testContextFileRepair() throws Exception {
    WorkingContextManager manager = getContextManager();
    manager.saveContext("foo", "bar");
    File file = manager.getContextFile();
    assertTrue(file.length() > 0);
    FileUtil.writeToFile(file, "123");   // corrupt it
    manager.saveContext("foo", "bar");
  }

  public void testXDebugger() {
    final WorkingContextManager manager = getContextManager();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(getProject()).getBreakpointManager();

    final XsltBreakpointType type = XBreakpointType.EXTENSION_POINT_NAME.findExtension(XsltBreakpointType.class);

    ApplicationManager.getApplication().runWriteAction(() -> {
      XLineBreakpointImpl<XBreakpointProperties> breakpoint =
        (XLineBreakpointImpl<XBreakpointProperties>)breakpointManager.addLineBreakpoint(type, "foo", 0, null);

      final String name = "foo";
      manager.saveContext(name, null);
      breakpointManager.removeBreakpoint(breakpoint);
    });
    manager.loadContext("foo");
    Collection<? extends XLineBreakpoint<XBreakpointProperties>> breakpoints = breakpointManager.getBreakpoints(type);
    assertEquals(1, breakpoints.size());
    manager.clearContext();
  }

  public void testContextFileName() {
    ProjectEx project = (ProjectEx)getProject();
    String name = project.getName();
    try {
      project.setProjectName("invalid | name");
      getContextManager().saveContext("foo", "bar");
    }
    finally {
      project.setProjectName(name);
    }
  }

  private WorkingContextManager getContextManager() {
    return WorkingContextManager.getInstance(getProject());
  }
}
