package com.intellij.ide.browsers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LocalFileUrl implements Url {
  private final String path;

  public LocalFileUrl(@NotNull String path) {
    this.path = path;
  }

  @NotNull
  @Override
  public String getPath() {
    return path;
  }

  @Override
  public boolean isInLocalFileSystem() {
    return true;
  }

  @Override
  public String toDecodedForm(boolean skipQueryAndFragment) {
    return path;
  }

  @NotNull
  @Override
  public String toExternalForm() {
    return path;
  }

  @NotNull
  @Override
  public String toExternalForm(boolean skipQueryAndFragment) {
    return toExternalForm();
  }

  @Nullable
  @Override
  public String getScheme() {
    return null;
  }

  @Nullable
  @Override
  public String getAuthority() {
    return null;
  }

  @Nullable
  @Override
  public String getParametersPart() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LocalFileUrl)) {
      return false;
    }
    return path.equals(((LocalFileUrl)o).path);
  }

  @Override
  public int hashCode() {
    return path.hashCode();
  }

  @Override
  public String toString() {
    return toExternalForm();
  }
}