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

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;

import javax.swing.*;
import java.util.List;

public class CacheLoader extends Loader {
  private final Loader myRepositoryLoader;

  public static Loader getInstance() {
    return ServiceManager.getService(Loader.class);
  }

  public CacheLoader() {
    super(SvnRepositoryCache.getInstance());
    myRepositoryLoader = new RepositoryLoader(myCache);
  }

  public void load(final RepositoryTreeNode node, final Expander expander) {
    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        final String nodeUrl = node.getURL().toString();

        final List<SVNDirEntry> cached = myCache.getChildren(nodeUrl);
        if (cached != null) {
          refreshNode(node, cached, expander);
        }
        final SVNErrorMessage error = myCache.getError(nodeUrl);
        if (error != null) {
          refreshNodeError(node, error);
        }
        // refresh anyway
        myRepositoryLoader.load(node, expander);
      }
    });
  }

  public void forceRefresh(final String repositoryRootUrl) {
    myCache.clear(repositoryRootUrl);
  }

  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.CACHED;
  }

  public Loader getRepositoryLoader() {
    return myRepositoryLoader;
  }
}
