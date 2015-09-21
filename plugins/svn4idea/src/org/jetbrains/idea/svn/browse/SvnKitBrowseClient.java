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
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
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
                   @Nullable Depth depth,
                   @Nullable DirectoryEntryConsumer handler) throws VcsException {
    assertUrl(target);

    SVNLogClient client = getLogClient();
    ISVNDirEntryHandler wrappedHandler = wrapHandler(handler);

    client.setIgnoreExternals(true);
    try {
      if (target.isFile()) {
        client.doList(target.getFile(), target.getPegRevision(), notNullize(revision), true, toDepth(depth), SVNDirEntry.DIRENT_ALL, wrappedHandler);
      }
      else {
        client.doList(target.getURL(), target.getPegRevision(), notNullize(revision), true, toDepth(depth), SVNDirEntry.DIRENT_ALL, wrappedHandler);
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @Override
  public long createDirectory(@NotNull SvnTarget target, @NotNull String message, boolean makeParents) throws VcsException {
    assertUrl(target);

    try {
      SVNCommitInfo commitInfo =
        myVcs.getSvnKitManager().createCommitClient().doMkDir(new SVNURL[]{target.getURL()}, message, null, makeParents);

      return commitInfo.getNewRevision();
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private SVNLogClient getLogClient() {
    ISVNAuthenticationManager authManager = myIsActive
                                            ? myVcs.getSvnConfiguration().getInteractiveManager(myVcs)
                                            : myVcs.getSvnConfiguration().getPassiveAuthenticationManager(myVcs.getProject());

    return myVcs.getSvnKitManager().createLogClient(authManager);
  }

  @Nullable
  private static ISVNDirEntryHandler wrapHandler(@Nullable DirectoryEntryConsumer handler) {
    return handler == null ? null : new SkipEmptyNameDirectoriesHandler(handler);
  }

  public static class SkipEmptyNameDirectoriesHandler implements ISVNDirEntryHandler {

    @NotNull private final DirectoryEntryConsumer handler;

    public SkipEmptyNameDirectoriesHandler(@NotNull DirectoryEntryConsumer handler) {
      this.handler = handler;
    }

    @Override
    public void handleDirEntry(SVNDirEntry dirEntry) throws SVNException {
      if (!isEmptyNameDirectory(dirEntry)) {
        handler.consume(DirectoryEntry.create(dirEntry));
      }
    }

    private static boolean isEmptyNameDirectory(@NotNull SVNDirEntry dirEntry) {
      return SVNNodeKind.DIR.equals(dirEntry.getKind()) && StringUtil.isEmpty(dirEntry.getName());
    }
  }
}