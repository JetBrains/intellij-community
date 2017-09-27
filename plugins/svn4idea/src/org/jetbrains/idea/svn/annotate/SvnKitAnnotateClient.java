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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.util.Date;

public class SvnKitAnnotateClient extends BaseSvnClient implements AnnotateClient {

  @Override
  public void annotate(@NotNull Target target,
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
          .doAnnotate(target.getUrl(), target.getPegRevision(), startRevision, endRevision, true, includeMergedRevisions,
                      toAnnotateHandler(handler), null);
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
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
