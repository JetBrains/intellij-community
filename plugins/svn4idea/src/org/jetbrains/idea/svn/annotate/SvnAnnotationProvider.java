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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.*;

import java.io.File;
import java.util.Date;

public class SvnAnnotationProvider implements AnnotationProvider {
  private final SvnVcs myVcs;

  public SvnAnnotationProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public FileAnnotation annotate(final VirtualFile file) throws VcsException {
    return annotate(file, new SvnFileRevision(myVcs, SVNRevision.HEAD, SVNRevision.HEAD, null, null, null, null, null));
  }

  public FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException(SvnBundle.message("exception.text.cannot.annotate.directory"));
    }
    final FileAnnotation[] annotation = new FileAnnotation[1];
    final SVNException[] exception = new SVNException[1];

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        try {
          final SvnFileAnnotation result = new SvnFileAnnotation(myVcs);

          final File ioFile = new File(file.getPath()).getAbsoluteFile();
          SVNWCClient wcClient = myVcs.createWCClient();
          SVNInfo info = wcClient.doInfo(ioFile, SVNRevision.WORKING);
          if (info == null) {
              exception[0] = new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "File ''{0}'' is not under version control", ioFile));
              return;
          }
          final String url = info.getURL() == null ? null : info.getURL().toString();

          SVNLogClient client = myVcs.createLogClient();
          SVNRevision endRevision = ((SvnRevisionNumber)revision.getRevisionNumber()).getRevision();
          if (progress != null) {
            progress.setText(SvnBundle.message("progress.text.computing.annotation", file.getName()));
          }
          client.doAnnotate(ioFile, SVNRevision.UNDEFINED,
                            SVNRevision.create(0), endRevision, new ISVNAnnotateHandler() {
            public void handleLine(Date date, long revision, String author, String line) {
              result.appendLineInfo(date, revision, author, line);
            }
          });

          client.doLog(new File[]{ioFile}, SVNRevision.HEAD, SVNRevision.create(1), false, false, 0,
                       new ISVNLogEntryHandler() {
                         public void handleLogEntry(SVNLogEntry logEntry) {
                           if (progress != null) {
                             progress.setText2(SvnBundle.message("progress.text2.revision.processed", logEntry.getRevision()));
                           }
                           result.setRevision(logEntry.getRevision(), new SvnFileRevision(myVcs, SVNRevision.UNDEFINED, logEntry, url, ""));
                         }
                       });

          annotation[0] = result;
        }
        catch (SVNException e) {
          exception[0] = e;
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(command, SvnBundle.message("action.text.annotate"), false, myVcs.getProject());
    }
    else {
      command.run();
    }
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return annotation[0];
  }

  public boolean isAnnotationValid( VcsFileRevision rev ){
    return true;
  }
}
