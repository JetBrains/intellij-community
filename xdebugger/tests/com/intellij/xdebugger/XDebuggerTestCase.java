package com.intellij.xdebugger;

import com.intellij.testFramework.LiteFixture;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerTestCase extends LiteFixture {
  protected static final MyLineBreakpointType MY_LINE_BREAKPOINT_TYPE = new MyLineBreakpointType();
  protected static final MySimpleBreakpointType MY_SIMPLE_BREAKPOINT_TYPE = new MySimpleBreakpointType();

  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtensionPoint(XBreakpointType.EXTENSION_POINT_NAME, XBreakpointType.class);
    registerExtension(XBreakpointType.EXTENSION_POINT_NAME, MY_LINE_BREAKPOINT_TYPE);
    registerExtension(XBreakpointType.EXTENSION_POINT_NAME, MY_SIMPLE_BREAKPOINT_TYPE);
  }

  public static class MyLineBreakpointType extends XLineBreakpointType<MyBreakpointProperties> {
    public MyLineBreakpointType() {
      super("testLine", "239");
    }

    public boolean canPutAt(@NotNull final VirtualFile file, final int line) {
      return false;
    }

    public MyBreakpointProperties createBreakpointProperties(@NotNull final VirtualFile file, final int line) {
      return null;
    }

    public MyBreakpointProperties createProperties() {
      return new MyBreakpointProperties();
    }

    public String getDisplayText(final XLineBreakpoint<MyBreakpointProperties> breakpoint) {
      return "";
    }
  }

  public static class MySimpleBreakpointType extends XBreakpointType<XBreakpoint<MyBreakpointProperties>,MyBreakpointProperties> {
    public MySimpleBreakpointType() {
      super("test", "239");
    }

    public String getDisplayText(final XBreakpoint<MyBreakpointProperties> breakpoint) {
      return "";
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
