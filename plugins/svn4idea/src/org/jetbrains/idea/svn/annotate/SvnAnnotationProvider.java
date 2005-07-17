package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Date;

public class SvnAnnotationProvider implements AnnotationProvider {
  private final SvnVcs myVcs;

  public SvnAnnotationProvider(final SvnVcs vcs) {
    myVcs = vcs;
  }

  public FileAnnotation annotate(final VirtualFile file) throws VcsException {
    return annotate(file, new SvnFileRevision(myVcs, SVNRevision.HEAD, SVNRevision.HEAD, null, null, null, null));
  }

  public FileAnnotation annotate(final VirtualFile file, final VcsFileRevision revision) throws VcsException {
    if (file.isDirectory()) {
      throw new VcsException("Annotation operation only makes sence for files");
    }
    final FileAnnotation[] annotation = new FileAnnotation[1];
    final SVNException[] exception = new SVNException[1];

    Runnable command = new Runnable() {
      public void run() {
        final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        try {
          final SvnFileAnnotation result = new SvnFileAnnotation(myVcs);

          SVNLogClient client = myVcs.createLogClient();
          SVNRevision endRevision = ((SvnRevisionNumber)revision.getRevisionNumber()).getRevision();
          if (progress != null) {
            progress.setText("Computing annotation for '" + file.getName() + "'");
          }
          client.doAnnotate(new File(file.getPath()).getAbsoluteFile(), SVNRevision.UNDEFINED,
                            SVNRevision.create(0), endRevision, new ISVNAnnotateHandler() {
            public void handleLine(Date date, long revision, String author, String line) {
              result.appendLineInfo(date, revision, author, line);
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
      ApplicationManager.getApplication().runProcessWithProgressSynchronously(command, "Annotate", false, myVcs.getProject());
    }
    else {
      command.run();
    }
    if (exception[0] != null) {
      throw new VcsException(exception[0]);
    }
    return annotation[0];
  }
}
