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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.info.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
* @author Konstantin Kolosovsky.
*/
public class DefaultBranchConfigInitializer implements Runnable {

  private static final Logger LOG = Logger.getInstance(DefaultBranchConfigInitializer.class);

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  private final Project myProject;
  private final NewRootBunch myBunch;
  private final VirtualFile myRoot;

  public DefaultBranchConfigInitializer(final Project project, final NewRootBunch bunch, final VirtualFile root) {
    myProject = project;
    myRoot = root;
    myBunch = bunch;
  }

  public void run() {
    final SvnBranchConfigurationNew result = loadDefaultConfiguration();
    if (result != null) {
      final Application application = ApplicationManager.getApplication();
      for (String url : result.getBranchUrls()) {
        application.executeOnPooledThread(new BranchesLoadRunnable(myProject, myBunch, url, InfoReliability.defaultValues, myRoot, null,
                                                                   true));
      }
      myBunch.updateForRoot(myRoot, new InfoStorage<SvnBranchConfigurationNew>(result, InfoReliability.defaultValues), null);
    }
  }

  @Nullable
  public SvnBranchConfigurationNew loadDefaultConfiguration() {
    try {
      final SvnVcs vcs = SvnVcs.getInstance(myProject);

      File rootFile = new File(myRoot.getPath());
      final Info info = vcs.getInfo(rootFile);
      if (info == null || info.getURL() == null) {
        LOG.info("Directory is not a working copy: " + myRoot.getPresentableUrl());
        return null;
      }
      SVNURL baseUrl = info.getURL();

      final SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
      result.setTrunkUrl(baseUrl.toString());
      while (true) {
        final String s = SVNPathUtil.tail(baseUrl.getPath());
        if (s.equalsIgnoreCase(DEFAULT_TRUNK_NAME) || s.equalsIgnoreCase(DEFAULT_BRANCHES_NAME) || s.equalsIgnoreCase(DEFAULT_TAGS_NAME)) {
          SVNURL rootPath = baseUrl.removePathTail();
          SvnTarget target = SvnTarget.fromURL(rootPath);

          vcs.getFactory(target).createBrowseClient().list(target, SVNRevision.HEAD, Depth.IMMEDIATES, createHandler(result, rootPath));
          break;
        }
        if (SVNPathUtil.removeTail(baseUrl.getPath()).length() == 0) {
          break;
        }
        baseUrl = baseUrl.removePathTail();
      }
      return result;
    }
    catch (SVNException e) {
      LOG.info(e);
      return null;
    }
    catch (VcsException e) {
      LOG.info(e);
      return null;
    }
  }

  @NotNull
  private static DirectoryEntryConsumer createHandler(final SvnBranchConfigurationNew result, final SVNURL rootPath) {
    return new DirectoryEntryConsumer() {

      @Override
      public void consume(final DirectoryEntry entry) throws SVNException {
        if (entry.isDirectory()) {
          SVNURL childUrl = rootPath.appendPath(entry.getName(), false);

          if (StringUtil.endsWithIgnoreCase(entry.getName(), DEFAULT_TRUNK_NAME)) {
            result.setTrunkUrl(childUrl.toString());
          }
          else {
            result.addBranches(childUrl.toString(),
                               new InfoStorage<List<SvnBranchItem>>(new ArrayList<SvnBranchItem>(0), InfoReliability.defaultValues));
          }
        }
      }
    };
  }
}
