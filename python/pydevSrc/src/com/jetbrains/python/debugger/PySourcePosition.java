// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PySourcePosition {

  private final String file;
  private final int line;

  protected PySourcePosition(final String file, final int line) {
    this.file = normalize(file);
    this.line = line;
  }

  protected @Nullable String normalize(@Nullable String file) {
    if (file == null) {
      return null;
    }

    return FileUtil.toSystemIndependentName(file);
  }

  public String getFile() {
    return file;
  }

  public int getLine() {
    return line;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PySourcePosition that)) return false;

    if (line != that.line) return false;
    if (file != null ? !file.equals(that.file) : that.file != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = file != null ? file.hashCode() : 0;
    result = 31 * result + line;
    return result;
  }

  @Override
  public String toString() {
    return "PySourcePosition(" + file + ":" + line + ")";
  }

  public static boolean isWindowsPath(@NotNull String path) {
    return path.contains("\\") || OSAgnosticPathUtil.startsWithWindowsDrive(path);
  }
}
