package com.jetbrains.python.debugger;

import com.intellij.openapi.util.io.FileUtil;

public abstract class PySourcePosition {

  private final String file;
  private final int line;

  protected PySourcePosition(final String file, final int line) {
    this.file = FileUtil.toSystemIndependentName(file);
    this.line = line;
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
    if (!(o instanceof PySourcePosition)) return false;

    PySourcePosition that = (PySourcePosition)o;

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
}
