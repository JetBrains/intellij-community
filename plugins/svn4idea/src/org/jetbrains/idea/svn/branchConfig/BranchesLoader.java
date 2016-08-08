/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.browse.BrowseClient;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class BranchesLoader implements Runnable {
  @NotNull private final Project myProject;
  @NotNull private final NewRootBunch myBunch;
  @NotNull private final VirtualFile myRoot;
  @NotNull private final String myUrl;
  @NotNull private final InfoReliability myInfoReliability;
  private final boolean myPassive;

  public BranchesLoader(@NotNull Project project,
                        @NotNull NewRootBunch bunch,
                        @NotNull String url,
                        @NotNull InfoReliability infoReliability,
                        @NotNull VirtualFile root,
                        boolean passive) {
    myProject = project;
    myBunch = bunch;
    myUrl = url;
    myInfoReliability = infoReliability;
    myRoot = root;
    myPassive = passive;
  }

  public void run() {
    try {
      List<SvnBranchItem> branches = loadBranches();
      myBunch.updateBranches(myRoot, myUrl, new InfoStorage<>(branches, myInfoReliability));
    }
    catch (VcsException e) {
      showError(e);
    }
    catch (SVNException e) {
      showError(e);
    }
  }

  @NotNull
  public List<SvnBranchItem> loadBranches() throws SVNException, VcsException {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    SVNURL branchesUrl = SVNURL.parseURIEncoded(myUrl);
    List<SvnBranchItem> result = new LinkedList<>();
    SvnTarget target = SvnTarget.fromURL(branchesUrl);
    DirectoryEntryConsumer handler = createConsumer(result);

    vcs.getFactory(target).create(BrowseClient.class, !myPassive).list(target, SVNRevision.HEAD, Depth.IMMEDIATES, handler);

    Collections.sort(result);
    return result;
  }

  private void showError(Exception e) {
    // already logged inside
    if (InfoReliability.setByUser.equals(myInfoReliability)) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "Branches load error: " + e.getMessage(), MessageType.ERROR);
    }
  }

  @NotNull
  private static DirectoryEntryConsumer createConsumer(@NotNull final List<SvnBranchItem> result) {
    return new DirectoryEntryConsumer() {

      @Override
      public void consume(final DirectoryEntry entry) throws SVNException {
        if (entry.getDate() != null) {
          result.add(new SvnBranchItem(entry.getUrl().toDecodedString(), entry.getDate(), entry.getRevision()));
        }
      }
    };
  }
}
