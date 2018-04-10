// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

/**
 * @author yole
 */
public class SvnRepositoryLocation implements RepositoryLocation {

  private final String myURL;
  @Nullable private final FilePath myRoot;

  public SvnRepositoryLocation(final String url) {
    this(url, null);
  }

  public SvnRepositoryLocation(String url, @Nullable FilePath root) {
    myURL = url;
    myRoot = root;
  }

  public String toString() {
    return myURL;
  }

  public String toPresentableString() {
    return myURL;
  }

  public String getURL() {
    return myURL;
  }

  public String getKey() {
    return myURL;
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

  public Url toSvnUrl() throws SvnBindException {
    return SvnUtil.createUrl(myURL);
  }
}
