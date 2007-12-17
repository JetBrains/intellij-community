package com.intellij.xdebugger;

import com.intellij.testFramework.LiteFixture;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author nik
 */
public abstract class XDebuggerTestCase extends LiteFixture {
  protected static final MyBreakpointType MY_BREAKPOINT_TYPE = new MyBreakpointType();

  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtension(XBreakpointType.EXTENSION_POINT_NAME, MY_BREAKPOINT_TYPE);
  }

  public static class MyBreakpointType extends XBreakpointType<MyBreakpointProperties> {
    public MyBreakpointType() {
      super("test", "239");
    }

    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }
  }

  protected static class MyBreakpointProperties extends XBreakpointProperties<MyBreakpointProperties> {
    @Attribute("option")
    public String myOption;

    public MyBreakpointProperties() {
    }

    public MyBreakpointProperties(final String option) {
      myOption = option;
    }

    public MyBreakpointProperties getState() {
      return this;
    }

    public void loadState(final MyBreakpointProperties state) {
      myOption = state.myOption;
    }
  }
}
