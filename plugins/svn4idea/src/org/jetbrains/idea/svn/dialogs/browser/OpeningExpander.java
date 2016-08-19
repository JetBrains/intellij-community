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
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.jetbrains.idea.svn.dialogs.browserCache.Expander;

import javax.swing.tree.TreeNode;
import java.util.LinkedList;
import java.util.List;

public class OpeningExpander extends AbstractOpeningExpander {
  private final List<String> pathElements;
  private final String myLongest;

  public OpeningExpander(final TreeNode[] path, final RepositoryBrowserComponent browser, final RepositoryTreeNode selectionPath) {
    super(browser, selectionPath.getURL().toString());
    pathElements = new LinkedList<>();

    for (TreeNode aPath : path) {
      RepositoryTreeNode node = (RepositoryTreeNode)aPath;
      pathElements.add(node.getURL().toString());
    }
    myLongest = pathElements.get(pathElements.size() - 1);
  }

  protected ExpandVariants expandNode(final String url) {
    if (pathElements.contains(url)) {
      if (myLongest.equals(url)) {
        return ExpandVariants.EXPAND_AND_EXIT;
      }
      return ExpandVariants.EXPAND_CONTINUE;
    }
    return ExpandVariants.DO_NOTHING;
  }

  protected boolean checkChild(final String childUrl) {
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
