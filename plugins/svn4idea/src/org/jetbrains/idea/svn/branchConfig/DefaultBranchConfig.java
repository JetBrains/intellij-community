// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
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

public final class DefaultBranchConfig {
  private static final Logger LOG = Logger.getInstance(DefaultBranchConfig.class);

  public static final @NlsSafe String TRUNK_NAME = "trunk";
  public static final @NlsSafe String BRANCHES_NAME = "branches";
  public static final @NlsSafe String TAGS_NAME = "tags";

  @Nullable
  public static SvnBranchConfigurationNew detect(@NotNull Project project, @NotNull VirtualFile root) {
    SvnBranchConfigurationNew result = null;
    SvnVcs vcs = SvnVcs.getInstance(project);
    Url rootUrl = SvnUtil.getUrl(vcs, VfsUtilCore.virtualToIoFile(root));

    if (rootUrl != null) {
      try {
        result = detect(vcs, rootUrl);
      }
      catch (VcsException e) {
        LOG.info(e);
      }
    }
    else {
      LOG.info("Directory is not a working copy: " + root.getPresentableUrl());
    }

    return result;
  }

  @NotNull
  private static SvnBranchConfigurationNew detect(@NotNull SvnVcs vcs, @NotNull Url url) throws VcsException {
    SvnBranchConfigurationNew result = new SvnBranchConfigurationNew();
    result.setTrunk(url);

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
    return name.equalsIgnoreCase(TRUNK_NAME) || name.equalsIgnoreCase(BRANCHES_NAME) || name.equalsIgnoreCase(TAGS_NAME);
  }

  @NotNull
  private static DirectoryEntryConsumer createHandler(@NotNull final SvnBranchConfigurationNew result, @NotNull final Url rootPath) {
    return entry -> {
      if (entry.isDirectory()) {
        Url childUrl = append(rootPath, entry.getName());

        if (StringUtil.endsWithIgnoreCase(entry.getName(), TRUNK_NAME)) {
          result.setTrunk(childUrl);
        }
        else {
          result.addBranches(childUrl, new InfoStorage<>(new ArrayList<>(0), InfoReliability.defaultValues));
        }
      }
    };
  }
}
