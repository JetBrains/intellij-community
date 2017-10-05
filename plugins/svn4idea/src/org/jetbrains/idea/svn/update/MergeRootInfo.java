/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class MergeRootInfo {
  private String myUrl1;
  private Revision myRevision1;
  private String myUrl2;
  private Revision myRevision2;

  public MergeRootInfo(File file, SvnVcs vcs) {
    myRevision1 = Revision.HEAD;
    myRevision2 = Revision.HEAD;

    Info info = vcs.getInfo(file);
    myUrl1 = info != null && info.getURL() != null ? info.getURL().toDecodedString() : "";
    myUrl2 = myUrl1;
  }

  public SVNURL getUrl1() {
    try {
      return createUrl(myUrl1, false);
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  public SVNURL getUrl2() {
    try {
      return createUrl(myUrl2, false);
    }
    catch (SvnBindException e) {
      return null;
    }
  }

  public Revision getRevision2() {
    return myRevision2;
  }

  public Revision getRevision1() {
    return myRevision1;
  }

  public long getResultRevision() {
    return myRevision2.getNumber();
  }

  public void setUrl1(final String text) {
    myUrl1 = text;
  }

  public void setUrl2(final String text) {
    myUrl2 = text;
  }

  public void setRevision1(final Revision rev) {
    myRevision1 = rev;
  }

  public void setRevision2(final Revision rev) {
    myRevision2 = rev;
  }

  public String getUrlString1() {
    return myUrl1;
  }

  public String getUrlString2() {
    return myUrl2;
  }

}
