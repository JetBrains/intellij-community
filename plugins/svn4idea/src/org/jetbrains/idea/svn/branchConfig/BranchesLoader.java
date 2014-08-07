/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BranchesLoader {

  private BranchesLoader() {
  }

  public static List<SvnBranchItem> loadBranches(final Project project, final String url, boolean passive) throws SVNException,
                                                                                                                  VcsException {
    final SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    final SvnVcs vcs = SvnVcs.getInstance(project);
    SVNURL branchesUrl = SVNURL.parseURIEncoded(url);
    List<SvnBranchItem> result = new LinkedList<SvnBranchItem>();
    SvnTarget target = SvnTarget.fromURL(branchesUrl);

    if (!passive) {
      // TODO: Implement ability to specify interactive/non-interactive auth mode for clients
      DirectoryEntryConsumer handler = createConsumer(branchesUrl, result);
      vcs.getFactory(target).createBrowseClient().list(target, SVNRevision.HEAD, Depth.IMMEDIATES, handler);
    }
    else {
      ISVNDirEntryHandler handler = createHandler(branchesUrl, result);
      SVNLogClient client = vcs.getSvnKitManager().createLogClient(configuration.getPassiveAuthenticationManager(project));
      client
        .doList(target.getURL(), target.getPegRevision(), SVNRevision.HEAD, false, SVNDepth.IMMEDIATES, SVNDirEntry.DIRENT_ALL, handler);
    }

    Collections.sort(result);
    return result;
  }

  @NotNull
  private static ISVNDirEntryHandler createHandler(@NotNull final SVNURL branchesUrl, @NotNull final List<SvnBranchItem> result) {
    return new ISVNDirEntryHandler() {
      public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
        // TODO: Remove equality check with branchesUrl when SVNLogClient will not be used directly, but rather through BrowseClient.
        if (!branchesUrl.equals(dirEntry.getURL()) && dirEntry.getDate() != null) {
          result.add(new SvnBranchItem(dirEntry.getURL().toDecodedString(), dirEntry.getDate(), dirEntry.getRevision()));
        }
      }
    };
  }

  @NotNull
  private static DirectoryEntryConsumer createConsumer(@NotNull final SVNURL branchesUrl, @NotNull final List<SvnBranchItem> result) {
    return new DirectoryEntryConsumer() {

      @Override
      public void consume(final DirectoryEntry entry) throws SVNException {
        // TODO: Remove equality check with branchesUrl when SVNLogClient will not be used directly, but rather through BrowseClient.
        if (!branchesUrl.equals(entry.getUrl()) && entry.getDate() != null) {
          result.add(new SvnBranchItem(entry.getUrl().toDecodedString(), entry.getDate(), entry.getRevision()));
        }
      }
    };
  }
}
