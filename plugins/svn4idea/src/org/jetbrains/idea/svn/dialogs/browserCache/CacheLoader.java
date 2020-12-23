// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.*;
import java.util.List;

public class CacheLoader extends Loader {

  @NotNull private final Loader myRepositoryLoader;

  public static Loader getInstance() {
    return ApplicationManager.getApplication().getService(Loader.class);
  }

  public CacheLoader() {
    super(SvnRepositoryCache.getInstance());
    myRepositoryLoader = new RepositoryLoader(myCache);
  }

  @Override
  public void load(@NotNull final RepositoryTreeNode node, @NotNull final Expander expander) {
    SwingUtilities.invokeLater(() -> {
      final List<DirectoryEntry> cached = myCache.getChildren(node.getURL());
      if (cached != null) {
        refreshNode(node, cached, expander);
      }
      final VcsException error = myCache.getError(node.getURL());
      if (error != null) {
        refreshNodeError(node, error);
      }
      // refresh anyway
      myRepositoryLoader.load(node, expander);
    });
  }

  @Override
  @NotNull
  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.CACHED;
  }
}
