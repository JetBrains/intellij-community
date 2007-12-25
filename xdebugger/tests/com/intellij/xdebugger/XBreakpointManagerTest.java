package com.intellij.xdebugger;

import com.intellij.mock.MockEditorFactory;
import com.intellij.mock.MockProject;
import com.intellij.mock.MockVirtualFileManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

/**
 * @author nik
 */
public class XBreakpointManagerTest extends XDebuggerTestCase {
  private XBreakpointManagerImpl myBreakpointManager;

  protected void setUp() throws Exception {
    super.setUp();
    MockProject project = disposeOnTearDown(new MockProject());
    MutablePicoContainer container = getApplication().getPicoContainer();
    registerComponentImplementation(container, EditorFactory.class, MockEditorFactory.class);
    registerComponentImplementation(container, VirtualFileManager.class, MockVirtualFileManager.class);
    myBreakpointManager = new XBreakpointManagerImpl(project, null);
  }

  public void testAddRemove() throws Exception {
    XLineBreakpoint<MyBreakpointProperties> lineBreakpoint =
      myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("123"));

    XBreakpoint<MyBreakpointProperties> breakpoint = myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("abc"));

    assertSameElements(myBreakpointManager.getAllBreakpoints(), breakpoint, lineBreakpoint);
    assertSame(lineBreakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE)));
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)));

    myBreakpointManager.removeBreakpoint(lineBreakpoint);
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getAllBreakpoints()));
    assertTrue(myBreakpointManager.getBreakpoints(MY_LINE_BREAKPOINT_TYPE).isEmpty());
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE)));

    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals(0, myBreakpointManager.getAllBreakpoints().length);
    assertTrue(myBreakpointManager.getBreakpoints(MY_SIMPLE_BREAKPOINT_TYPE).isEmpty());
  }

  public void testSerialize() throws Exception {
    myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "myurl", 239, new MyBreakpointProperties("abc"));
    myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("123"));

    Element element = XmlSerializer.serialize(myBreakpointManager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    XBreakpointManagerImpl.BreakpointManagerState managerState = XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class);
    myBreakpointManager.loadState(managerState);
    XBreakpoint<?>[] breakpoints = myBreakpointManager.getAllBreakpoints();
    assertEquals(2, breakpoints.length);

    XLineBreakpoint lineBreakpoint = assertInstanceOf(breakpoints[0], XLineBreakpoint.class);
    assertEquals(239, lineBreakpoint.getLine());
    assertEquals("myurl", lineBreakpoint.getFileUrl());
    assertEquals("abc", assertInstanceOf(lineBreakpoint.getProperties(), MyBreakpointProperties.class).myOption);

    assertEquals("123", assertInstanceOf(breakpoints[1].getProperties(), MyBreakpointProperties.class).myOption);
  }

  public void testListener() throws Exception {
    final StringBuilder out = new StringBuilder();
    XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>> listener = new XBreakpointAdapter<XLineBreakpoint<MyBreakpointProperties>>() {
      public void breakpointAdded(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("added[").append(breakpoint.getProperties().myOption).append("];");
      }

      public void breakpointRemoved(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("removed[").append(breakpoint.getProperties().myOption).append("];");
      }

      public void breakpointChanged(@NotNull final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("changed[").append(breakpoint.getProperties().myOption).append("];");
      }
    };
    myBreakpointManager.addBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);

    XBreakpoint<MyBreakpointProperties> breakpoint = myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("abc"));
    myBreakpointManager.addBreakpoint(MY_SIMPLE_BREAKPOINT_TYPE, new MyBreakpointProperties("321"));
    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals("added[abc];removed[abc];", out.toString());

    myBreakpointManager.removeBreakpointListener(MY_LINE_BREAKPOINT_TYPE, listener);
    out.setLength(0);
    myBreakpointManager.addLineBreakpoint(MY_LINE_BREAKPOINT_TYPE, "url", 239, new MyBreakpointProperties("a"));
    assertEquals("", out.toString());
  }
}
