/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
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
        file = file.toLowerCase();
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
  public final PySourcePosition convertToPython(@NotNull final XSourcePosition position) {
    return convertToPython(convertFilePath(position.getFile().getPath()), convertLocalLineToRemote(position.getFile(), position.getLine()));
  }

  @NotNull
  protected PySourcePosition convertToPython(@NotNull String filePath, int line) {
    return new PyLocalSourcePosition(filePath, line);
  }

  protected static int convertLocalLineToRemote(VirtualFile file, int line) {
    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        while (PyDebugSupportUtils.isContinuationLine(document, line)) {
          line++;
        }
      }
      return line + 1;
    }
    finally {
      lock.finish();
    }
  }

  @Nullable
  public XSourcePosition convertFromPython(@NotNull final PySourcePosition position) {
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

  @Nullable
  protected static XSourcePosition createXSourcePosition(@Nullable VirtualFile vFile, int line) {
    if (vFile != null) {
      return XDebuggerUtil.getInstance().createPosition(vFile, convertRemoteLineToLocal(vFile, line));
    }
    else {
      return null;
    }
  }

  private static int convertRemoteLineToLocal(final VirtualFile vFile, int line) {
    final Document document = ApplicationManager.getApplication().runReadAction(new Computable<Document>() {
      @Override
      public Document compute() {
        return FileDocumentManager.getInstance().getDocument(vFile);
      }
    });

    line--;
    if (document != null) {
      while (PyDebugSupportUtils.isContinuationLine(document, line - 1)) {
        line--;
      }
    }
    return line;
  }
}
