package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XLineBreakpointImpl<T extends XBreakpointProperties> extends XBreakpointBase<T, XLineBreakpointImpl.LineBreakpointState> implements XLineBreakpoint<T> {
  public XLineBreakpointImpl(final XBreakpointType<T> type, String url, int line, final @Nullable T properties) {
    super(type, properties, new LineBreakpointState(true, type.getId(), url, line));
  }

  private XLineBreakpointImpl(final XBreakpointType<T> type, final LineBreakpointState breakpointState) {
    super(type, breakpointState);
  }

  public int getLine() {
    return getState().getLine();
  }

  public String getFileUrl() {
    return getState().getFileUrl();
  }

  @Tag("line-breakpoint")
  public static class LineBreakpointState extends XBreakpointBase.BreakpointState {
    private String myFileUrl;
    private int myLine;

    public LineBreakpointState() {
    }

    public LineBreakpointState(final boolean enabled, final String typeId, final String fileUrl, final int line) {
      super(enabled, typeId);
      myFileUrl = fileUrl;
      myLine = line;
    }

    @Tag("url")
    public String getFileUrl() {
      return myFileUrl;
    }

    public void setFileUrl(final String fileUrl) {
      myFileUrl = fileUrl;
    }

    @Tag("line")
    public int getLine() {
      return myLine;
    }

    public void setLine(final int line) {
      myLine = line;
    }

    public <T extends XBreakpointProperties> XBreakpointBase<T, ?> createBreakpoint(@NotNull final XBreakpointType<T> type) {
      return new XLineBreakpointImpl<T>(type, this);
    }
  }
}
