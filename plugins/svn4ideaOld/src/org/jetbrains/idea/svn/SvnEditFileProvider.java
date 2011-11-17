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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.EditFileProvider;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;

/**
 * @author alex
 */
public class SvnEditFileProvider implements EditFileProvider {
  private final SvnVcs myVCS;

  public SvnEditFileProvider(SvnVcs vcs) {
    myVCS = vcs;
  }

  public void editFiles(VirtualFile[] files) throws VcsException {
    File[] ioFiles = new File[files.length];
    SVNWCClient client = myVCS.createWCClient();
    for (int i = 0; i < files.length; i++) {
      ioFiles[i] = new File(files[i].getPath());
      try {
        SVNPropertyData property = client
          .doGetProperty(ioFiles[i], SVNProperty.NEEDS_LOCK, SVNRevision.WORKING, SVNRevision.WORKING);
        if (property == null || property.getValue() == null) {
          throw new VcsException(SvnBundle.message("exception.text.file.miss.svn", ioFiles[i].getName()));
        }
      }
      catch (SVNException e) {
        throw new VcsException(e);
      }
    }
    SvnUtil.doLockFiles(myVCS.getProject(), myVCS, ioFiles);
  }

  public String getRequestText() {
    return SvnBundle.message("confirmation.text.edit.file");
  }
}
