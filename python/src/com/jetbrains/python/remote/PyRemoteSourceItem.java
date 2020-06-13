// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.remote;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.remote.RemoteFile;
import com.intellij.util.PathMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class PyRemoteSourceItem {
  private final String myPath;
  private final String myRootPath;
  private final int mySize;
  private String myLocalPrefix = null;


  public PyRemoteSourceItem(String path, String rootPath, int size) {
    myPath = path;
    myRootPath = rootPath;
    mySize = size;
  }

  public boolean shouldCopy(String basePath) {
    File localFile = getLocalFile(basePath);
    return shouldCopyTo(localFile);
  }

  public boolean shouldCopyTo(File localFile) {
    return !localFile.exists() || localFile.length() != getSize();
  }

  public File getLocalFile(String basePath) {
    return new File(basePath, getLocalRelativePath());
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

  public String getLocalRelativePath() {
    if (myLocalPrefix != null) {
      return myLocalPrefix + (getModule().startsWith("?") ? "" : "/") + getModule();
    }
    else {
      return getModule();
    }
  }

  public String getModule() {
    return myPath.substring(myRootPath.length());
  }

  public String getArcPath() {
    return getLocalRelativePath();
  }

  public void setLocalPrefix(String localPrefix) {
    localPrefix = StringUtil.trimEnd(localPrefix, "/");
    myLocalPrefix = localPrefix;
  }

  public String getLocalPrefix() {
    return myLocalPrefix;
  }

  public void addRootMappingTo(@NotNull PathMappingSettings settings, @NotNull String sourcesLocalPath) {
    settings.addMappingCheckUnique(
      FileUtil.toCanonicalPath(new File(sourcesLocalPath, generateRootFolderName()).getAbsolutePath()), getRootPath());
  }

  public static String localPathForRemoteRoot(@NotNull String sourcesLocalPath, @NotNull String remoteRoot) {
    return FileUtil.toCanonicalPath(new File(sourcesLocalPath, generateRootFolderNameFor(remoteRoot)).getAbsolutePath());
  }

  public String generateRootFolderName() {
    if (getPath().equals(getRootPath())) // this is an egg for example
    {
      return RemoteFile.createRemoteFile(getPath()).getName();
    }
    else {
      return generateRootFolderNameFor(getRootPath());
    }
  }

  @NotNull
  private static String generateRootFolderNameFor(String path) {
    return String.valueOf(path.hashCode());
  }
}
