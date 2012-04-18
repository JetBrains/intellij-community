/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.dialogs.browserCache;

import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.List;

public abstract class Loader {
  protected final SvnRepositoryCache myCache;

  protected Loader(final SvnRepositoryCache cache) {
    myCache = cache;
  }

  public abstract void load(final RepositoryTreeNode node, final Expander afterRefreshExpander);
  public abstract void forceRefresh(final String repositoryRootUrl);
  protected abstract NodeLoadState getNodeLoadState();

  protected void refreshNodeError(final RepositoryTreeNode node, final SVNErrorMessage text) {
    if (node.isDisposed()) {
      return;
    }
    final RepositoryTreeNode existingNode = node.getNodeWithSamePathUnderModelRoot();
    if (existingNode == null) {
      return;
    }
    if (existingNode.isDisposed()) {
      return;
    }

    existingNode.setErrorNode(text, getNodeLoadState());
  }

  protected void refreshNode(final RepositoryTreeNode node, final List<SVNDirEntry> data, final Expander expander) {
    if (node.isDisposed()) {
      return;
    }
    final RepositoryTreeNode existingNode = node.getNodeWithSamePathUnderModelRoot();
    if (existingNode == null) {
      return;
    }

    if (existingNode.isDisposed()) {
      return;
    }
    expander.onBeforeRefresh(existingNode);
    existingNode.setChildren(data, getNodeLoadState());
    expander.onAfterRefresh(existingNode);
  }
}
