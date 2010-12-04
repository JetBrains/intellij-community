package com.jetbrains.python.debugger;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyLocalPositionConverter implements PyPositionConverter {

  protected static class PyLocalSourcePosition extends PySourcePosition {
    PyLocalSourcePosition(final String file, final int line) {
      super(file, line);
    }
  }

  protected static class PyRemoteSourcePosition extends PySourcePosition {
    PyRemoteSourcePosition(final String file, final int line) {
      super(file, line);
    }
  }

  @NotNull
  final public PySourcePosition create(@NotNull final String file, final int line) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(file);
    if (vFile != null) {
      return new PyLocalSourcePosition(vFile.getPath(), line);
    }
    else {
      return new PyRemoteSourcePosition(file, line);
    }
  }

  @NotNull
  public PySourcePosition convert(@NotNull final XSourcePosition position) {
    return new PyLocalSourcePosition(position.getFile().getPath(), position.getLine() + 1);
  }

  @Nullable
  public XSourcePosition convert(@NotNull final PySourcePosition position) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(position.getFile());
    return createXSourcePosition(vFile, position.getLine());
  }

  @Nullable
  protected static XSourcePosition createXSourcePosition(VirtualFile vFile, int line) {
    if (vFile != null) {
      return XDebuggerUtil.getInstance().createPosition(vFile, line - 1);
    }
    else {
      return null;
    }
  }
}
