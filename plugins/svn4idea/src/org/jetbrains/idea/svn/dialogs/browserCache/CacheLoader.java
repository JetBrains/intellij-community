// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.*;
import java.util.List;

public class CacheLoader extends Loader {

  @NotNull private final Loader myRepositoryLoader;

  public static Loader getInstance() {
    return ServiceManager.getService(Loader.class);
  }

  public CacheLoader() {
    super(SvnRepositoryCache.getInstance());
    myRepositoryLoader = new RepositoryLoader(myCache);
  }

  @Override
  public void load(@NotNull final RepositoryTreeNode node, @NotNull final Expander expander) {
    SwingUtilities.invokeLater(() -> {
      final String nodeUrl = node.getURL().toString();

      final List<DirectoryEntry> cached = myCache.getChildren(nodeUrl);
      if (cached != null) {
        refreshNode(node, cached, expander);
      }
      final String error = myCache.getError(nodeUrl);
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
