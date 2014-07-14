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

import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
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
  private final VirtualFile myCurrentFile;

  public SvnRemoteFileAnnotation(SvnVcs vcs, String contents, VcsRevisionNumber baseRevision, final VirtualFile currentFile) {
    super(vcs, contents, baseRevision);
    myCurrentFile = currentFile;
  }

  @Override
  protected void showAllAffectedPaths(SvnRevisionNumber number) {
    ShowAllAffectedGenericAction.showSubmittedFiles(myVcs.getProject(), number, myCurrentFile, SvnVcs.getKey(), null, false);
  }

  @Override
  public void dispose() {
  }

  @Override
  public VirtualFile getFile() {
    return myCurrentFile;
  }
}
