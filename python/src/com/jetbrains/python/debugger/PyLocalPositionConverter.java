// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;


public class PyLocalPositionConverter implements PyPositionConverter {
  private final static String[] EGG_EXTENSIONS = new String[]{".egg", ".zip"};

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
        file = winNormCase(file);
      }
      return super.normalize(file);
    }
  }

  protected static class PyRemoteSourcePosition extends PySourcePosition {
    public PyRemoteSourcePosition(final String file, final int line) {
      super(file, line);
    }

    @Override
    protected String normalize(@Nullable String file) {
      if (file == null) {
        return null;
      }
      if (SystemInfo.isWindows && isWindowsPath(file)) {
        file = winNormCase(file);
      }
      return super.normalize(file);
    }
  }

  @NotNull
  @Override
  public PySourcePosition create(@NotNull String file, int line) {
    return convertPythonToFrame(file, line);
  }

  @Override
  @NotNull
  public PySourcePosition convertPythonToFrame(@NotNull final String filePath, final int line) {
    File file = new File(filePath);

    if (file.exists()) {
      return new PyLocalSourcePosition(file.getPath(), line);
    }
    else {
      return new PyRemoteSourcePosition(filePath, line);
    }
  }

  @NotNull
  @Override
  public PySourcePosition convertFrameToPython(@NotNull PySourcePosition position) {
    return position; // frame and Python positions are the same for Python files
  }

  @Override
  @NotNull
  public PySourcePosition convertToPython(@NotNull final XSourcePosition position) {
    return convertToPython(convertFilePath(position.getFile().getPath()), convertLocalLineToRemote(position.getFile(), position.getLine()));
  }

  @NotNull
  protected PySourcePosition convertToPython(@NotNull String filePath, int line) {
    return new PyLocalSourcePosition(filePath, line);
  }

  protected static int convertLocalLineToRemote(VirtualFile file, int l) {
    return ReadAction.compute(() -> {
      int line = l;
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        while (PyDebugSupportUtils.isContinuationLine(document, line)) {
          line++;
        }
      }
      return line + 1;
    });
  }

  @Override
  @Nullable
  public XSourcePosition convertFromPython(@NotNull final PySourcePosition position, String frameName) {
    return createXSourcePosition(getVirtualFile(position.getFile()), position.getLine());
  }

  @Override
  public PySignature convertSignature(PySignature signature) {
    return signature;
  }

  public VirtualFile getVirtualFile(String path) {
    VirtualFile vFile = getLocalFileSystem().findFileByPath(path);

    if (vFile == null) {
      vFile = findEggEntry(path);
    }
    return vFile;
  }

  protected VirtualFileSystem getLocalFileSystem() {
    return LocalFileSystem.getInstance();
  }

  private VirtualFile findEggEntry(String file) {
    int ind = -1;
    for (String ext : EGG_EXTENSIONS) {
      ind = file.indexOf(ext);
      if (ind != -1) break;
    }
    if (ind != -1) {
      String jarPath = file.substring(0, ind + 4);
      VirtualFile jarFile = getLocalFileSystem().findFileByPath(jarPath);
      if (jarFile != null) {
        String innerPath = file.substring(ind + 4);
        final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
        if (jarRoot != null) {
          return jarRoot.findFileByRelativePath(innerPath);
        }
      }
    }
    return null;
  }

  private static String convertFilePath(String file) {
    int ind = -1;
    for (String ext : EGG_EXTENSIONS) {
      ind = file.indexOf(ext + "!");
      if (ind != -1) break;
    }
    if (ind != -1) {
      return file.substring(0, ind + 4) + file.substring(ind + 5);
    }
    else {
      return file;
    }
  }

  private static String winNormCase(String file) {
    int ind = -1;
    for (String ext : EGG_EXTENSIONS) {
      ind = file.indexOf(ext);
      if (ind != -1) break;
    }
    if (ind != -1) {
      return StringUtil.toLowerCase(file.substring(0, ind + 4)) + file.substring(ind + 4);
    }
    else {
      return StringUtil.toLowerCase(file);
    }
  }
  @Nullable
  public static XSourcePosition createXSourcePosition(@Nullable VirtualFile vFile, int line) {
    if (vFile != null) {
      return XDebuggerUtil.getInstance().createPosition(vFile, convertRemoteLineToLocal(vFile, line));
    }
    else {
      return null;
    }
  }

  private static int convertRemoteLineToLocal(final VirtualFile vFile, int line) {
    final Document document =
      ReadAction.compute(() -> FileDocumentManager.getInstance().getDocument(vFile));

    line--;
    if (document != null) {
      while (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
        line--;
      }
    }
    return line;
  }
}
