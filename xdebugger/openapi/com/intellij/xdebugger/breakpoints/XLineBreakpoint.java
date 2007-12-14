package com.intellij.xdebugger.breakpoints;

/**
 * @author nik
 */
public interface XLineBreakpoint<T extends XBreakpointProperties> extends XBreakpoint<T> {

  int getLine();

  String getFileUrl();

}
