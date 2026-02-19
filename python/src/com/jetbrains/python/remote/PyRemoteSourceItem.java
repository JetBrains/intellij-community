// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class PyRemoteSourceItem {
  private final String myPath;
  private final String myRootPath;
  private final int mySize;


  public PyRemoteSourceItem(String path, String rootPath, int size) {
    myPath = path;
    myRootPath = rootPath;
    mySize = size;
  }

  public String getPath() {
    return myPath;
  }

  public String getRootPath() {
    return myRootPath;
  }

  public int getSize() {
    return mySize;
  }

  public String getModule() {
    return myPath.substring(myRootPath.length());
  }

  public static String localPathForRemoteRoot(@NotNull String sourcesLocalPath, @NotNull String remoteRoot) {
    return FileUtil.toCanonicalPath(new File(sourcesLocalPath, generateRootFolderNameFor(normalize(remoteRoot))).getAbsolutePath());
  }

  private static @NotNull String normalize(@NotNull String path) {
    String systemIndependentPathForm = FileUtil.toSystemIndependentName(path);
    if (systemIndependentPathForm.equals("/")) {
      return systemIndependentPathForm;
    }
    else {
      return StringUtil.trimEnd(systemIndependentPathForm, "/");
    }
  }

  private static @NotNull String generateRootFolderNameFor(String path) {
    return String.valueOf(path.hashCode());
  }
}
