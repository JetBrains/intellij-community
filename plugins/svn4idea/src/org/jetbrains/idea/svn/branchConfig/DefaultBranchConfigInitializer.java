// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.browse.DirectoryEntryConsumer;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.util.ArrayList;

import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.jetbrains.idea.svn.SvnUtil.removePathTail;

public class DefaultBranchConfigInitializer implements Runnable {

  private static final Logger LOG = Logger.getInstance(DefaultBranchConfigInitializer.class);

  @NonNls private static final String DEFAULT_TRUNK_NAME = "trunk";
  @NonNls private static final String DEFAULT_BRANCHES_NAME = "branches";
  @NonNls private static final String DEFAULT_TAGS_NAME = "tags";

  @NotNull private final Project myProject;
  @NotNull private final NewRootBunch myBunch;
  @NotNull private final VirtualFile myRoot;

  public DefaultBranchConfigInitializer(@NotNull Project project, @NotNull NewRootBunch bunch, @NotNull VirtualFile root) {
    myProject = project;
    myRoot = root;
    myBunch = bunch;
  }

  public void run() {
    SvnBranchConfigurationNew configuration = getDefaultConfiguration();

    if (configuration != null) {
      for (String url : configuration.getBranchUrls()) {
        myBunch.reloadBranchesAsync(myRoot, url, InfoReliability.defaultValues);
      }

      myBunch.updateForRoot(myRoot, new InfoStorage<>(configuration, InfoReliability.defaultValues), false);
    }
  }

  @Nullable
  public SvnBranchConfigurationNew getDefaultConfiguration() {
    SvnBranchConfigurationNew result = null;
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    Url rootUrl = SvnUtil.getUrl(vcs, VfsUtilCore.virtualToIoFile(myRoot));

    if (rootUrl != null) {
      try {
        result = getDefaultConfiguration(vcs, rootUrl);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
    else {
      LOG.info("Directory is not a working copy: " + myRoot.getPresentableUrl());
    }

    return result;
  }

  @NotNull
  private static SvnBranchConfigurationNew getDefaultConfiguration(@NotNull SvnVcs vcs, @NotNull Url url) throws VcsException {
    SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
    result.setTrunkUrl(url.toString());

    Url branchLocationsParent = getBranchLocationsParent(url);
    if (branchLocationsParent != null) {
      Target target = Target.on(branchLocationsParent);

      vcs.getFactory(target).createBrowseClient().list(target, Revision.HEAD, Depth.IMMEDIATES, createHandler(result, target.getUrl()));
    }

    return result;
  }

  @Nullable
  private static Url getBranchLocationsParent(@NotNull Url url) throws SvnBindException {
    while (!hasEmptyName(url) && !hasDefaultName(url)) {
      url = removePathTail(url);
    }

    return hasDefaultName(url) ? removePathTail(url) : null;
  }

  private static boolean hasEmptyName(@NotNull Url url) {
    return StringUtil.isEmpty(url.getTail());
  }

  private static boolean hasDefaultName(@NotNull Url url) {
    String name = url.getTail();

    return name.equalsIgnoreCase(DEFAULT_TRUNK_NAME) ||
           name.equalsIgnoreCase(DEFAULT_BRANCHES_NAME) ||
           name.equalsIgnoreCase(DEFAULT_TAGS_NAME);
  }

  @NotNull
  private static DirectoryEntryConsumer createHandler(@NotNull final SvnBranchConfigurationNew result, @NotNull final Url rootPath) {
    return entry -> {
      if (entry.isDirectory()) {
        Url childUrl = append(rootPath, entry.getName());

        if (StringUtil.endsWithIgnoreCase(entry.getName(), DEFAULT_TRUNK_NAME)) {
          result.setTrunkUrl(childUrl.toString());
        }
        else {
          result.addBranches(childUrl.toString(),
                             new InfoStorage<>(new ArrayList<>(0), InfoReliability.defaultValues));
        }
      }
    };
  }
}
