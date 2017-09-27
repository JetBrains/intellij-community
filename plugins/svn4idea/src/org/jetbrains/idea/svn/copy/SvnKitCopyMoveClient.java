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
package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

public class SvnKitCopyMoveClient extends BaseSvnClient implements CopyMoveClient {

  private static final int INVALID_REVISION = -1;

  @Override
  public void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException {
    final SVNCopySource copySource = new SVNCopySource(isMove ? SVNRevision.UNDEFINED : SVNRevision.WORKING, SVNRevision.WORKING, src);

    try {
      myVcs.getSvnKitManager().createCopyClient().doCopy(new SVNCopySource[]{copySource}, dst, isMove, makeParents, true);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  @Override
  public long copy(@NotNull Target source,
                   @NotNull Target destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   boolean isMove,
                   @NotNull String message,
                   @Nullable CommitEventHandler handler) throws VcsException {

    if (!destination.isUrl()) {
      throw new IllegalArgumentException("Only urls are supported as destination " + destination);
    }

    final SVNCopySource copySource = createCopySource(source, revision);
    SVNCopyClient client = myVcs.getSvnKitManager().createCopyClient();
    client.setEventHandler(toEventHandler(handler));

    SVNCommitInfo info;
    try {
      info = client
        .doCopy(new SVNCopySource[]{copySource}, destination.getUrl(), isMove, makeParents, true, message, null);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }

    return info != null ? info.getNewRevision() : INVALID_REVISION;
  }

  @Override
  public void copy(@NotNull Target source,
                   @NotNull File destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   @Nullable ProgressTracker handler) throws VcsException {
    SVNCopyClient client = myVcs.getSvnKitManager().createCopyClient();
    client.setEventHandler(toEventHandler(handler));

    try {
      client.doCopy(new SVNCopySource[]{createCopySource(source, revision)}, destination, false, makeParents, true);
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }

  @NotNull
  private static SVNCopySource createCopySource(@NotNull Target source, @Nullable SVNRevision revision) {
    return source.isFile()
           ? new SVNCopySource(source.getPegRevision(), revision, source.getFile())
           : new SVNCopySource(source.getPegRevision(), revision, source.getUrl());
  }
}
