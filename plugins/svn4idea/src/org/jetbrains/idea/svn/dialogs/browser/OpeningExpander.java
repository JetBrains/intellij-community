// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;

import javax.swing.tree.TreeNode;
import java.util.LinkedList;
import java.util.List;

public class OpeningExpander extends AbstractOpeningExpander {
  private final List<Url> pathElements;
  private final Url myLongest;

  public OpeningExpander(final TreeNode[] path, final RepositoryBrowserComponent browser, final RepositoryTreeNode selectionPath) {
    super(browser, selectionPath.getURL());
    pathElements = new LinkedList<>();

    for (TreeNode aPath : path) {
      RepositoryTreeNode node = (RepositoryTreeNode)aPath;
      pathElements.add(node.getURL());
    }
    myLongest = pathElements.get(pathElements.size() - 1);
  }

  @Override
  protected ExpandVariants expandNode(@NotNull Url url) {
    if (pathElements.contains(url)) {
      if (myLongest.equals(url)) {
        return ExpandVariants.EXPAND_AND_EXIT;
      }
      return ExpandVariants.EXPAND_CONTINUE;
    }
    return ExpandVariants.DO_NOTHING;
  }

  @Override
  protected boolean checkChild(@NotNull Url childUrl) {
    return pathElements.contains(childUrl);
  }
  
  public static class Factory implements NotNullFunction<RepositoryBrowserComponent, Expander> {
    private final TreeNode[] myPath;
    private final RepositoryTreeNode mySelectionPath;

    public Factory(final TreeNode[] path, final RepositoryTreeNode selectionPath) {
      myPath = path;
      mySelectionPath = selectionPath;
    }

    @NotNull
    public Expander fun(final RepositoryBrowserComponent repositoryBrowserComponent) {
      return new OpeningExpander(myPath, repositoryBrowserComponent, mySelectionPath);
    }
  }
}
