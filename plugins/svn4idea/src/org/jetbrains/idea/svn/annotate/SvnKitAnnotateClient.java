package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Date;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitAnnotateClient extends BaseSvnClient implements AnnotateClient {

  @Override
  public void annotate(@NotNull SvnTarget target,
                       @NotNull SVNRevision startRevision,
                       @NotNull SVNRevision endRevision,
                       boolean includeMergedRevisions,
                       @Nullable DiffOptions diffOptions,
                       @Nullable AnnotationConsumer handler) throws VcsException {
    try {
      SVNLogClient client = myVcs.getSvnKitManager().createLogClient();

      client.setDiffOptions(toDiffOptions(diffOptions));
      if (target.isFile()) {
        client
          .doAnnotate(target.getFile(), target.getPegRevision(), startRevision, endRevision, true, includeMergedRevisions,
                      toAnnotateHandler(handler), null);
      }
      else {
        client
          .doAnnotate(target.getURL(), target.getPegRevision(), startRevision, endRevision, true, includeMergedRevisions,
                      toAnnotateHandler(handler), null);
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Nullable
  private static ISVNAnnotateHandler toAnnotateHandler(@Nullable final AnnotationConsumer handler) {
    ISVNAnnotateHandler result = null;

    if (handler != null) {
      result = new ISVNAnnotateHandler() {
        @Override
        public void handleLine(Date date, long revision, String author, String line) {
          // deprecated - not called
        }

        @Override
        public void handleLine(Date date,
                               long revision,
                               String author,
                               String line,
                               Date mergedDate,
                               long mergedRevision,
                               String mergedAuthor,
                               String mergedPath,
                               int lineNumber) throws SVNException {
          if (revision > 0) {
            CommitInfo info = new CommitInfo.Builder(revision, date, author).build();
            CommitInfo mergeInfo = mergedDate != null ? new CommitInfo.Builder(mergedRevision, mergedDate, mergedAuthor).build() : null;

            handler.consume(lineNumber, info, mergeInfo);
          }
        }

        @Override
        public boolean handleRevision(Date date, long revision, String author, File contents) {
          return false;
        }

        @Override
        public void handleEOF() {
        }
      };
    }

    return result;
  }
}
