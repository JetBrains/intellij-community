package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class PyLocalPositionConverter implements PyPositionConverter {

  protected static class PyLocalSourcePosition extends PySourcePosition {
    public PyLocalSourcePosition(final String file, final int line) {
      super(file, line);
    }

    @Override
    protected String normalize(@Nullable String file) {
      if (file == null) {
        return null;
      }
      if (SystemInfo.isWindows) {
        file = file.toLowerCase();
      }
      return super.normalize(file);
    }
  }

  protected static class PyRemoteSourcePosition extends PySourcePosition {
    PyRemoteSourcePosition(final String file, final int line) {
      super(file, line);
    }

    @Override
    protected String normalize(@Nullable String file) {
      if (file == null) {
        return null;
      }
      if (isWindowsPath(file)) {  //TODO: add SystemInfo.isWindows condition and fix path lowercasing for remote win debugger and local unix host (PY-4244)
        file = file.toLowerCase();
      }
      return super.normalize(file);
    }
  }

  @NotNull
  final public PySourcePosition create(@NotNull final String filePath, final int line) {
    File file = new File(filePath);

    if (file.exists()) {
      return new PyLocalSourcePosition(file.getPath(), line);
    }
    else {
      return new PyRemoteSourcePosition(filePath, line);
    }
  }

  @NotNull
  public PySourcePosition convert(@NotNull final XSourcePosition position) {
    return new PyLocalSourcePosition(position.getFile().getPath(), convertLocalLineToRemote(position.getFile(), position.getLine()));
  }

  protected static int convertLocalLineToRemote(VirtualFile file, int line) {
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
  protected static XSourcePosition createXSourcePosition(@Nullable VirtualFile vFile, int line) {
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
