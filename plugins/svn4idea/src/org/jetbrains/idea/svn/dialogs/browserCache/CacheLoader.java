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
