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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNLogClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class BranchesLoader {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.BranchesLoader");

  private BranchesLoader() {
  }

  public static List<SvnBranchItem> loadBranches(final Project project, final String url, boolean passive) throws SVNException {
    final List<SvnBranchItem> result = new LinkedList<SvnBranchItem>();

    final SvnConfiguration configuration = SvnConfiguration.getInstance(project);
    final SvnVcs vcs = SvnVcs.getInstance(project);
    final ISVNAuthenticationManager passiveManager = passive ?
      configuration.getPassiveAuthenticationManager(project) : configuration.getInteractiveManager(vcs);
    final SVNURL branchesUrl = SVNURL.parseURIEncoded(url);

    // TODO: Currently this method works for 1.8 - but should be updated to command line implementation
    final SVNLogClient logClient = vcs.createLogClient(passiveManager);
    logClient.doList(branchesUrl, SVNRevision.UNDEFINED, SVNRevision.HEAD, false, SVNDepth.IMMEDIATES, SVNDirEntry.DIRENT_ALL, new ISVNDirEntryHandler() {
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
