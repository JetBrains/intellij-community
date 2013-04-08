package com.intellij.ide.browsers;

import com.intellij.ide.browsers.Url;
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
}
