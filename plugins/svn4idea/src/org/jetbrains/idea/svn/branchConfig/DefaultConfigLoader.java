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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.integrate.SvnBranchItem;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultConfigLoader {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.DefaultConfigLoader");

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  private DefaultConfigLoader() {
  }

  @Nullable
  public static SvnBranchConfigurationNew loadDefaultConfiguration(final Project project, final VirtualFile vcsRoot) {
    try {
      final SvnVcs vcs = SvnVcs.getInstance(project);

      File rootFile = new File(vcsRoot.getPath());
      final SVNInfo info = vcs.getInfo(rootFile);
      if (info == null || info.getURL() == null) {
        LOG.info("Directory is not a working copy: " + vcsRoot.getPresentableUrl());
        return null;
      }
      SVNURL baseUrl = info.getURL();

      final SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
      result.setTrunkUrl(baseUrl.toString());
      while(true) {
        final String s = SVNPathUtil.tail(baseUrl.getPath());
        if (s.equalsIgnoreCase(DEFAULT_TRUNK_NAME) || s.equalsIgnoreCase(DEFAULT_BRANCHES_NAME) || s.equalsIgnoreCase(DEFAULT_TAGS_NAME)) {
          SVNURL rootPath = baseUrl.removePathTail();
          SvnTarget target = SvnTarget.fromURL(rootPath);

          vcs.getFactory(target).createBrowseClient().list(target, SVNRevision.HEAD, SVNDepth.IMMEDIATES, createHandler(result, rootPath));
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
  private static ISVNDirEntryHandler createHandler(final SvnBranchConfigurationNew result, final SVNURL rootPath) {
    return new ISVNDirEntryHandler() {
      public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
        if (SVNNodeKind.DIR.equals(dirEntry.getKind())) {
          SVNURL childUrl = rootPath.appendPath(dirEntry.getName(), false);

          if (StringUtil.endsWithIgnoreCase(dirEntry.getName(), DEFAULT_TRUNK_NAME)) {
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
