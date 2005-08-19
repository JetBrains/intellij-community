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
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileContent;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;

public class SvnDiffProvider implements DiffProvider {
  private final SvnVcs myVcs;

  public SvnDiffProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public VcsRevisionNumber getCurrentRevision(VirtualFile file) {
    return new SvnRevisionNumber(SVNRevision.BASE);
  }

  public VcsRevisionNumber getLastRevision(VirtualFile file) {
    return new SvnRevisionNumber(SVNRevision.HEAD);
  }

  public VcsFileContent createFileContent(final VcsRevisionNumber revisionNumber, final VirtualFile selectedFile) {
    final SVNRevision svnRevision = ((SvnRevisionNumber)revisionNumber).getRevision();
    return new VcsFileContent() {
      private byte[] myContent;

      public void loadContent() throws VcsException {
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        File file = new File(selectedFile.getPath()).getAbsoluteFile();
        ConentLoader loader = new ConentLoader(file, contents, svnRevision);
        if (ApplicationManager.getApplication().isDispatchThread() &&
            !svnRevision.isLocal()) {
          ApplicationManager.getApplication().runProcessWithProgressSynchronously(loader,
                                                                                  SvnBundle.message("progress.title.loading.file.content"), false, null);
        }
        else {
          loader.run();
        }
        if (loader.getException() != null) {
          throw new VcsException(loader.getException());
        }
        myContent = contents.toByteArray();
      }

      public byte[] getContent() {
        return myContent;
      }
    };
  }

  private class ConentLoader implements Runnable {
    private SVNRevision myRevision;
    private File myFile;
    private OutputStream myDst;
    private SVNException myException;

    public ConentLoader(File file, OutputStream dst, SVNRevision revision) {
      myFile = file;
      myDst = dst;
      myRevision = revision;
    }

    public SVNException getException() {
      return myException;
    }

    public void run() {
      ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
      if (progress != null) {
        progress.setText(SvnBundle.message("progress.text.loading.contents", myFile.getName()));
        progress.setText2(SvnBundle.message("progress.text2.revision.information", myRevision));
      }
      try {
        SVNWCClient client = myVcs.createWCClient();
        client.doGetFileContents(myFile, SVNRevision.UNDEFINED, myRevision, true, myDst);
      }
      catch (SVNException e) {
        myException = e;
      }
    }
  }
}
