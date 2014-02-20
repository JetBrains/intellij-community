/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.browse;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitBrowseClient extends BaseSvnClient implements BrowseClient {
  @Override
  public void list(@NotNull SvnTarget target,
                   @Nullable SVNRevision revision,
                   @Nullable SVNDepth depth,
                   @Nullable ISVNDirEntryHandler handler) throws VcsException {
    assertUrl(target);

    SVNLogClient client = myVcs.createLogClient();
    ISVNDirEntryHandler wrappedHandler = wrapHandler(handler);

    try {
      if (target.isFile()) {
        client.doList(target.getFile(), target.getPegRevision(), revision, true, depth, SVNDirEntry.DIRENT_ALL, wrappedHandler);
      }
      else {
        client.doList(target.getURL(), target.getPegRevision(), revision, true, depth, SVNDirEntry.DIRENT_ALL, wrappedHandler);
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Nullable
  private static ISVNDirEntryHandler wrapHandler(@Nullable ISVNDirEntryHandler handler) {
    return handler == null ? null : new SkipEmptyNameDirectoriesHandler(handler);
  }

  public static class SkipEmptyNameDirectoriesHandler implements ISVNDirEntryHandler {

    @NotNull private final ISVNDirEntryHandler handler;

    public SkipEmptyNameDirectoriesHandler(@NotNull ISVNDirEntryHandler handler) {
      this.handler = handler;
    }

    @Override
    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
      if (!isEmptyNameDirectory(dirEntry)) {
        handler.handleDirEntry(dirEntry);
      }
    }

    private static boolean isEmptyNameDirectory(SVNDirEntry dirEntry) {
      return SVNNodeKind.DIR.equals(dirEntry.getKind()) && StringUtil.isEmpty(dirEntry.getName());
    }
  }
}