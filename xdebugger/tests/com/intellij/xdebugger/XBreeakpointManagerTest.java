package com.intellij.xdebugger;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import org.jdom.Element;

/**
 * @author nik
 */
public class XBreeakpointManagerTest extends UsefulTestCase {

  public void testSerialize() throws Exception {
    XBreakpointManagerImpl manager = new XBreakpointManagerImpl();
    XLineBreakpointProperties properties = new XLineBreakpointProperties("myurl", 239);
    manager.addBreakpoint(XBreakpointType.LINE_BREAKPOINT_TYPE, properties);

    Element element = XmlSerializer.serialize(manager.getState());
    XBreakpointManagerImpl.BreakpointManagerState managerState = XmlSerializer.deserialize(element, XBreakpointManagerImpl.BreakpointManagerState.class);
    manager.loadState(managerState);
    XBreakpoint<XLineBreakpointProperties> breakpoint = assertOneElement(manager.getBreakpoints());
    assertEquals(239, breakpoint.getProperties().getLine());
    assertEquals("myurl", breakpoint.getProperties().getFileUrl());
  }


}
