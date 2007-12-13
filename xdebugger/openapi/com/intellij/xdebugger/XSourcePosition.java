package com.intellij.xdebugger;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author nik
 */
public interface XSourcePosition {

  int getLine();

  int getOffset();

  VirtualFile getFile();

}
