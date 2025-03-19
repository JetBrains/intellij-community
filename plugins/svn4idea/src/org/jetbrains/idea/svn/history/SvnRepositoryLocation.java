// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public final class SvnRepositoryLocation implements RepositoryLocation {
  private final String myUrlValue;
  private final @Nullable Url myUrl;
  private final @Nullable Url myRepositoryUrl;
  private final @Nullable FilePath myRoot;

  public SvnRepositoryLocation(@NotNull String url) {
    myUrl = null;
    myUrlValue = url;
    myRepositoryUrl = null;
    myRoot = null;
  }

  public SvnRepositoryLocation(@NotNull Url url) {
    this(url, null, null);
  }

  public SvnRepositoryLocation(@NotNull Url url, @Nullable Url repositoryUrl, @Nullable FilePath root) {
    myUrl = url;
    myUrlValue = url.toString();
    myRepositoryUrl = repositoryUrl;
    myRoot = root;
  }

  @Override
  public String toString() {
    return myUrlValue;
  }

  @Override
  public @NotNull String toPresentableString() {
    return myUrlValue;
  }

  public String getURL() {
    return myUrlValue;
  }

  @Override
  public String getKey() {
    return myUrlValue;
  }

  public @Nullable FilePath getRoot() {
    return myRoot;
  }

  public @Nullable Url getRepositoryUrl() {
    return myRepositoryUrl;
  }

  public @NotNull Url toSvnUrl() throws SvnBindException {
    return myUrl != null ? myUrl : SvnUtil.createUrl(myUrlValue);
  }
}
