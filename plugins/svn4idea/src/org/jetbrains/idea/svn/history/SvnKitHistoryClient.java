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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitHistoryClient extends BaseSvnClient implements HistoryClient {

  @Override
  public void doLog(@NotNull SvnTarget target,
                    @NotNull SVNRevision startRevision,
                    @NotNull SVNRevision endRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable LogEntryConsumer handler) throws VcsException {
    try {
      SVNLogClient client = myVcs.getSvnKitManager().createLogClient();

      if (target.isFile()) {
        client.doLog(new File[]{target.getFile()}, startRevision, endRevision, target.getPegRevision(), stopOnCopy, discoverChangedPaths,
                     includeMergedRevisions, limit, revisionProperties, toHandler(handler));
      }
      else {
        client.doLog(target.getURL(), ArrayUtil.EMPTY_STRING_ARRAY, target.getPegRevision(), startRevision, endRevision, stopOnCopy,
                     discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, toHandler(handler));
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Nullable
  private static ISVNLogEntryHandler toHandler(@Nullable final LogEntryConsumer handler) {
    ISVNLogEntryHandler result = null;

    if (handler != null) {
      result = logEntry -> {
        try {
          handler.consume(LogEntry.create(logEntry));
        }
        catch (SvnBindException e) {
          throw e.toSVNException();
        }
      };
    }

    return result;
  }
}
