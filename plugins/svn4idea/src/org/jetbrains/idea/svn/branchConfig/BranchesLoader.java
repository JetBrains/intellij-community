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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BranchesLoader {
  private static Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.BranchesLoader");

  private BranchesLoader() {
  }

  public static List<SvnBranchItem> loadBranches(final Project project, final String url) throws SVNException {
    final List<SvnBranchItem> result = new LinkedList<SvnBranchItem>();
    final ProjectLevelVcsManager vcsManager = PeriodicalTasksCloser.safeGetComponent(project, ProjectLevelVcsManager.class);
    final SvnVcs svnVcs = (SvnVcs) vcsManager.findVcsByName(SvnVcs.VCS_NAME);
    final SVNLogClient logClient = svnVcs.createLogClient();
    final SVNURL branchesUrl = SVNURL.parseURIEncoded(url);
    logClient.doList(branchesUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD, false, false, new ISVNDirEntryHandler() {
      public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
        final SVNURL currentUrl = dirEntry.getURL();
        if (! branchesUrl.equals(currentUrl)) {
          final String url = currentUrl.toString();
          // if have permissions
          if (dirEntry.getDate() != null) {
            result.add(new SvnBranchItem(url, dirEntry.getDate(), dirEntry.getRevision()));
          }
        }
      }
    });
    Collections.sort(result);
    return result;
  }
}
