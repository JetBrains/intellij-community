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
package org.jetbrains.idea.svn.update;

import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

public class UpdateRootInfo {
  private String myUrl;
  private SVNRevision myRevision;
  private boolean myUpdateToSpecifiedRevision = false;

  public UpdateRootInfo(File file, SvnVcs vcs) {
    myRevision = SVNRevision.HEAD;

    SVNInfo info = vcs.getInfo(file);
    myUrl = info != null && info.getURL() != null ? info.getURL().toString() : "";
  }

  public SVNURL getUrl() {
    try {
      return SVNURL.parseURIEncoded(myUrl);
    }
    catch (SVNException e) {
      return null;
    }
  }

  public String getUrlAsString() {
    return myUrl;
  }

  public SVNRevision getRevision() {
    return myRevision;
  }

  public boolean isUpdateToRevision() {
    return myUpdateToSpecifiedRevision;
  }

  public void setUrl(final String text) {
    myUrl = text;
  }

  public void setUpdateToRevision(final boolean value) {
    myUpdateToSpecifiedRevision = value;
  }

  public void setRevision(final SVNRevision svnRevision) {
    myRevision =svnRevision;
  }
}
