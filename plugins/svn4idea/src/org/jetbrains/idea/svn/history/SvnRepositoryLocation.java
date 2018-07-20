// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public class SvnRepositoryLocation implements RepositoryLocation {

  private final String myUrlValue;
  @Nullable private final Url myUrl;
  @Nullable private final Url myRepositoryUrl;
  @Nullable private final FilePath myRoot;

  public SvnRepositoryLocation(final String url) {
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

  public String toString() {
    return myUrlValue;
  }

  public String toPresentableString() {
    return myUrlValue;
  }

  public String getURL() {
    return myUrlValue;
  }

  public String getKey() {
    return myUrlValue;
  }

  @Nullable
  public FilePath getRoot() {
    return myRoot;
  }

  @Override
  public void onBeforeBatch() {
  }

  @Override
  public void onAfterBatch() {
  }

  @Nullable
  public Url getRepositoryUrl() {
    return myRepositoryUrl;
  }

  @NotNull
  public Url toSvnUrl() throws SvnBindException {
    return myUrl != null ? myUrl : SvnUtil.createUrl(myUrlValue);
  }
}
