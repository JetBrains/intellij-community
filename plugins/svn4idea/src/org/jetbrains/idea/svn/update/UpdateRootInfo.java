/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNInputFileChannel;
import org.jetbrains.idea.svn.SvnVcs;

import java.io.File;

public class UpdateRootInfo {
  private String myUrl;
  private SVNRevision myRevision;
  private boolean myUpdateToSpecifiedRevision = false;

  public UpdateRootInfo(File file, SvnVcs vcs) {
    myRevision = SVNRevision.HEAD;
    try {
      SVNWCClient wcClient = vcs.createWCClient();
      SVNInfo info = wcClient.doInfo(file, SVNRevision.WORKING);
      if (info != null) {
        final SVNURL url = info.getURL();
        myUrl = url.toString();
      } else {
        myUrl = "";
      }
    }
    catch (SVNException e) {
      myUrl = "";
    }

  }

  public SVNURL getUrl() {
    try {
      return SVNURL.parseURIDecoded(myUrl);
    }
    catch (SVNException e) {
      return null;
    }
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
