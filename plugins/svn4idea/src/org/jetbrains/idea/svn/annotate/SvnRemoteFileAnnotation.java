/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.vfs.VcsFileSystem;
import com.intellij.openapi.vcs.vfs.VcsVirtualFile;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnChangeList;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/20/12
 * Time: 12:10 PM
 */
public class SvnRemoteFileAnnotation extends BaseSvnFileAnnotation {
  private final SvnChangeList mySvnChangeList;
  private final String myPathToSelect;

  public SvnRemoteFileAnnotation(SvnVcs vcs, String contents, VcsRevisionNumber baseRevision, SvnChangeList svnChangeList,
                                 final String pathToSelect) {
    super(vcs, contents, baseRevision);
    mySvnChangeList = svnChangeList;
    myPathToSelect = pathToSelect;
  }

  @Override
  protected void showAllAffectedPaths(SvnRevisionNumber number) {
    final AbstractVcsHelper instance = AbstractVcsHelper.getInstance(myVcs.getProject());
    final String title = VcsBundle.message("paths.affected.in.revision", myBaseRevision.asString());
    instance.showChangesListBrowser(mySvnChangeList,
      new VcsVirtualFile(myPathToSelect, myContents.getBytes(CharsetToolkit.UTF8_CHARSET), number.asString(), VcsFileSystem.getInstance()), title);
  }

  @Override
  public void dispose() {
  }
}
