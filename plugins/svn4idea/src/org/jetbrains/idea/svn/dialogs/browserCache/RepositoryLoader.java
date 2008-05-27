package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;

import javax.swing.*;
import java.util.*;

class RepositoryLoader extends Loader {
  // may be several requests if: several same-level nodes are expanded simultaneosly; or browser can be opening into some expanded state
  private final Queue<Pair<RepositoryTreeNode, Expander>> myLoadQueue;
  private boolean myQueueProcessorActive;

  RepositoryLoader(final SvnRepositoryCache cache) {
    super(cache);

    myLoadQueue = new LinkedList<Pair<RepositoryTreeNode, Expander>>();
    myQueueProcessorActive = false;
  }

  public void load(final RepositoryTreeNode node, final Expander afterRefreshExpander) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = new Pair<RepositoryTreeNode, Expander>(node, afterRefreshExpander);
    if (! myQueueProcessorActive) {
      ApplicationManager.getApplication().executeOnPooledThread(new LoadTask(data));
      myQueueProcessorActive = true;
    } else {
      myLoadQueue.offer(data);
    }
  }

  private void setResults(final Pair<RepositoryTreeNode, Expander> data, final List<SVNDirEntry> children) {
    myCache.put(data.first.getURL().toString(), children);
    refreshNode(data.first, children, data.second);
  }

  private void setError(final Pair<RepositoryTreeNode, Expander> data, final SVNErrorMessage message) {
    myCache.put(data.first.getURL().toString(), message);
    refreshNodeError(data.first, message);
  }

  private void startNext() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = myLoadQueue.poll();
    if (data == null) {
      myQueueProcessorActive = false;
      return;
    }
    if (data.first.isDisposed()) {
      // ignore if node is already disposed
      startNext();
    } else {
      ApplicationManager.getApplication().executeOnPooledThread(new LoadTask(data));
    }
  }

  public void forceRefresh(final String repositoryRootUrl) {
    // ? remove
  }

  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.REFRESHED;
  }

  private class LoadTask implements Runnable {
    private final Pair<RepositoryTreeNode, Expander> myData;

    private LoadTask(final Pair<RepositoryTreeNode, Expander> data) {
      myData = data;
    }

    public void run() {
      final Collection<SVNDirEntry> entries = new TreeSet<SVNDirEntry>();
      try {
        final RepositoryTreeNode node = myData.first;

        node.getRepository().getDir((node.getSVNDirEntry() == null) ? "" : node.getPath(), -1, null, new ISVNDirEntryHandler() {
          public void handleDirEntry(final SVNDirEntry dirEntry) throws SVNException {
            entries.add(dirEntry);
          }
        });
      } catch (final SVNException e) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            setError(myData, e.getErrorMessage());
            startNext();
          }
        });
        return;
      }

      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          setResults(myData, new ArrayList<SVNDirEntry>(entries));
          startNext();
        }
      });
    }
  }
}
