package com.intellij.tokenindex;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class PathMarkerToken extends Token {
  private final String myPath;

  public PathMarkerToken(@NotNull String path) {
    super(-1, -1);
    myPath = path;
  }

  public String getPath() {
    return myPath;
  }

  @Override
  public String toString() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PathMarkerToken that = (PathMarkerToken)o;

    if (!myPath.equals(that.myPath)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myPath.hashCode();
  }
}
