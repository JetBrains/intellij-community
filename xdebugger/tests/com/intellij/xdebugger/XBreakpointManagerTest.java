package com.intellij.xdebugger;

import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointAdapter;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.intellij.mock.MockProject;
import com.intellij.mock.MockEditorFactory;
import com.intellij.mock.MockVirtualFileManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.vfs.VirtualFileManager;
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
    myBreakpointManager = new XBreakpointManagerImpl(project);
  }

  public void testAddRemove() throws Exception {
    XBreakpoint<MyBreakpointProperties> breakpoint =
      myBreakpointManager.addBreakpoint(MY_BREAKPOINT_TYPE, new MyBreakpointProperties("123"));
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getAllBreakpoints()));
    assertSame(breakpoint, assertOneElement(myBreakpointManager.getBreakpoints(MY_BREAKPOINT_TYPE)));

    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals(0, myBreakpointManager.getAllBreakpoints().length);
    assertTrue(myBreakpointManager.getBreakpoints(MY_BREAKPOINT_TYPE).isEmpty());
  }

  public void testSerialize() throws Exception {
    myBreakpointManager.addLineBreakpoint(MY_BREAKPOINT_TYPE, "myurl", 239, new MyBreakpointProperties("abc"));

    Element element = XmlSerializer.serialize(myBreakpointManager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    XBreakpointManagerImpl.BreakpointManagerState managerState = XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class);
    myBreakpointManager.loadState(managerState);
    XLineBreakpoint breakpoint = assertInstanceOf(assertOneElement(myBreakpointManager.getAllBreakpoints()), XLineBreakpoint.class);
    assertEquals(239, breakpoint.getLine());
    assertEquals("myurl", breakpoint.getFileUrl());
    assertEquals("abc", assertInstanceOf(breakpoint.getProperties(), MyBreakpointProperties.class).myOption);
  }

  public void testListener() throws Exception {
    final StringBuilder out = new StringBuilder();
    XBreakpointAdapter<MyBreakpointProperties> listener = new XBreakpointAdapter<MyBreakpointProperties>() {
      public void breakpointAdded(@NotNull final XBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("added[").append(breakpoint.getProperties().myOption).append("];");
      }

      public void breakpointRemoved(@NotNull final XBreakpoint<MyBreakpointProperties> breakpoint) {
        out.append("removed[").append(breakpoint.getProperties().myOption).append("];");
      }
    };
    myBreakpointManager.addBreakpointListener(MY_BREAKPOINT_TYPE, listener);

    XBreakpoint<MyBreakpointProperties> breakpoint = myBreakpointManager.addBreakpoint(MY_BREAKPOINT_TYPE, new MyBreakpointProperties("abc"));
    myBreakpointManager.removeBreakpoint(breakpoint);
    assertEquals("added[abc];removed[abc];", out.toString());

    myBreakpointManager.removeBreakpointListener(MY_BREAKPOINT_TYPE, listener);
    out.setLength(0);
    myBreakpointManager.addBreakpoint(MY_BREAKPOINT_TYPE, new MyBreakpointProperties("a"));
    assertEquals("", out.toString());
  }
}
