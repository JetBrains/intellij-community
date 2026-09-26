// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.remote;

import com.intellij.remote.RemoteFile;
import com.intellij.util.PathMapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public final class PyCommandLineStateUtil {

  private PyCommandLineStateUtil() { }


  public static @NotNull String remapStuffPathsList(@NotNull String pathsValue, @NotNull PathMapper pathMapper, @NotNull String interpreterPath) {
    boolean isWin = RemoteFile.isWindowsPath(interpreterPath);
    List<String> mappedPaths = new ArrayList<>();
    for (String path : pathsValue.split(Pattern.quote("|"))) {
      mappedPaths.add(RemoteFile.createRemoteFile(pathMapper.convertToRemote(path), isWin).getPath());
    }
    return String.join("|", mappedPaths);
  }
}
