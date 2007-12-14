package com.intellij.xdebugger;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;

/**
 * @author nik
 */
public class XBreeakpointManagerTest extends UsefulTestCase {

  public void testSerialize() throws Exception {
    XBreakpointManagerImpl manager = new XBreakpointManagerImpl();
    manager.addLineBreakpoint(XBreakpointType.LINE_BREAKPOINT_TYPE, "myurl", 239, null);

    Element element = XmlSerializer.serialize(manager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));
    XBreakpointManagerImpl.BreakpointManagerState managerState = XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class);
    manager.loadState(managerState);
    XLineBreakpoint breakpoint = assertInstanceOf(assertOneElement(manager.getBreakpoints()), XLineBreakpoint.class);
    assertEquals(239, breakpoint.getLine());
    assertEquals("myurl", breakpoint.getFileUrl());
  }


}
