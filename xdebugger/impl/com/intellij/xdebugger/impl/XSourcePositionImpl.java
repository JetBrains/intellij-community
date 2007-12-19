package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public class XSourcePositionImpl implements XSourcePosition {
  public int getLine() {
    throw new UnsupportedOperationException("'getLine' not implemented in " + getClass().getName());
  }

  public int getOffset() {
    throw new UnsupportedOperationException("'getOffset' not implemented in " + getClass().getName());
  }

  public VirtualFile getFile() {
    throw new UnsupportedOperationException("'getFile' not implemented in " + getClass().getName());
  }
}
