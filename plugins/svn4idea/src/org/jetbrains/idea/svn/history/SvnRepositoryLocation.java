/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

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
  public void onBeforeBatch() throws VcsException {
  }

  @Override
  public void onAfterBatch() {
  }

  @Nullable
  public static FilePath getLocalPath(final String fullPath, final NotNullFunction<File, Boolean> detector, final SvnVcs vcs) {
    if (vcs.getProject().isDefault()) return null;
    final RootUrlInfo rootForUrl = vcs.getSvnFileUrlMapping().getWcRootForUrl(fullPath);
    FilePath result = null;

    if (rootForUrl != null) {
      String relativePath = SvnUtil.getRelativeUrl(rootForUrl.getUrl(), fullPath);
      File file = new File(rootForUrl.getPath(), relativePath);

      result = VcsContextFactory.SERVICE.getInstance().createFilePathOn(file, detector);
    }

    return result;
  }

  public SVNURL toSvnUrl() throws SvnBindException {
    return SvnUtil.createUrl(myURL);
  }
}
