// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.auth.SvnAuthenticationProvider;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import javax.swing.*;
import java.util.List;
import java.util.Queue;

import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.containers.ContainerUtil.sorted;

class RepositoryLoader extends Loader {
  // may be several requests if: several same-level nodes are expanded simultaneosly; or browser can be opening into some expanded state
  @NotNull private final Queue<Pair<RepositoryTreeNode, Expander>> myLoadQueue;
  private boolean myQueueProcessorActive;

  RepositoryLoader(@NotNull SvnRepositoryCache cache) {
    super(cache);

    myLoadQueue = ContainerUtil.newLinkedList();
    myQueueProcessorActive = false;
  }

  @Override
  public void load(@NotNull RepositoryTreeNode node, @NotNull Expander afterRefreshExpander) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final Pair<RepositoryTreeNode, Expander> data = Pair.create(node, afterRefreshExpander);
    if (! myQueueProcessorActive) {
      startLoadTask(data);
      myQueueProcessorActive = true;
    } else {
      myLoadQueue.offer(data);
    }
  }

  private void setResults(@NotNull Pair<RepositoryTreeNode, Expander> data, @NotNull List<DirectoryEntry> children) {
    myCache.put(data.first.getURL().toString(), children);
    refreshNode(data.first, children, data.second);
  }

  private void setError(@NotNull Pair<RepositoryTreeNode, Expander> data, @NotNull String message) {
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
      startLoadTask(data);
    }
  }

  private void startLoadTask(@NotNull final Pair<RepositoryTreeNode, Expander> data) {
    final ModalityState state = ModalityState.current();
    ApplicationManager.getApplication()
      .executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(new LoadTask(data), new EmptyProgressIndicator(state)));
  }

  @Override
  @NotNull
  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.REFRESHED;
  }

  private class LoadTask implements Runnable {

    @NotNull private final Pair<RepositoryTreeNode, Expander> myData;

    private LoadTask(@NotNull Pair<RepositoryTreeNode, Expander> data) {
      myData = data;
    }

    @Override
    public void run() {
      List<DirectoryEntry> entries = newArrayList();
      final RepositoryTreeNode node = myData.first;
      final SvnVcs vcs = node.getVcs();
      SvnAuthenticationProvider.forceInteractive();

      try {
        Target target = Target.on(node.getURL());
        vcs.getFactoryFromSettings().createBrowseClient().list(target, Revision.HEAD, Depth.IMMEDIATES, entries::add);
      }
      catch (final VcsException e) {
        SwingUtilities.invokeLater(() -> {
          setError(myData, e.getMessage());
          startNext();
        });
        return;
      } finally {
        SvnAuthenticationProvider.clearInteractive();
      }

      SwingUtilities.invokeLater(() -> {
        setResults(myData, sorted(entries, DirectoryEntry.CASE_INSENSITIVE_ORDER));
        startNext();
      });
    }
  }
}
