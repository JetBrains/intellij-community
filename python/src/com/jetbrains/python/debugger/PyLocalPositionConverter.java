package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyLocalPositionConverter implements PyPositionConverter {

  protected static class PyLocalSourcePosition extends PySourcePosition {
    public PyLocalSourcePosition(final String file, final int line) {
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
    return new PyLocalSourcePosition(position.getFile().getPath(), convertLocalLineToRemote(position.getFile(), position.getLine()));
  }

  protected int convertLocalLineToRemote(VirtualFile file, int line) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      while (PyDebugSupportUtils.isContinuationLine(document, line)) {
        line++;
      }
    }
    return line + 1;
  }

  @Nullable
  public XSourcePosition convert(@NotNull final PySourcePosition position) {
    final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(position.getFile());
    return createXSourcePosition(vFile, position.getLine());
  }

  @Nullable
  protected static XSourcePosition createXSourcePosition(VirtualFile vFile, int line) {
    if (vFile != null) {
      return XDebuggerUtil.getInstance().createPosition(vFile, convertRemoteLineToLocal(vFile, line));
    }
    else {
      return null;
    }
  }

  private static int convertRemoteLineToLocal(VirtualFile vFile, int line) {
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    line--;
    if (document != null) {
      while (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
        line--;
      }
    }
    return line;
  }
}
