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
package org.jetbrains.idea.svn.checkout;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;

public class SvnKitExportClient extends BaseSvnClient implements ExportClient {

  @Override
  public void export(@NotNull Target from,
                     @NotNull File to,
                     @Nullable SVNRevision revision,
                     @Nullable Depth depth,
                     @Nullable String nativeLineEnd,
                     boolean force,
                     boolean ignoreExternals,
                     @Nullable ProgressTracker handler) throws VcsException {
    SVNUpdateClient client = myVcs.getSvnKitManager().createUpdateClient();

    client.setEventHandler(toEventHandler(handler));
    client.setIgnoreExternals(ignoreExternals);

    try {
      if (from.isFile()) {
        client.doExport(from.getFile(), to, from.getPegRevision(), revision, nativeLineEnd, force, toDepth(depth));
      }
      else {
        client.doExport(from.getUrl(), to, from.getPegRevision(), revision, nativeLineEnd, force, toDepth(depth));
      }
    }
    catch (SVNException e) {
      throw new SvnBindException(e);
    }
  }
}
