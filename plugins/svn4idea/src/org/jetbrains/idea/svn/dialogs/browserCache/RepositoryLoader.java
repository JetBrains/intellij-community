// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.browse.DirectoryEntry;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static com.intellij.util.containers.ContainerUtil.sorted;

class RepositoryLoader extends Loader {
  // may be several requests if: several same-level nodes are expanded simultaneosly; or browser can be opening into some expanded state
  @NotNull private final Queue<Pair<RepositoryTreeNode, Expander>> myLoadQueue;
  private boolean myQueueProcessorActive;

  RepositoryLoader(@NotNull SvnRepositoryCache cache) {
    super(cache);

    myLoadQueue = new LinkedList<>();
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
    myCache.put(data.first.getURL(), children);
    refreshNode(data.first, children, data.second);
  }

  private void setError(@NotNull Pair<RepositoryTreeNode, Expander> data, @NotNull String message) {
    myCache.put(data.first.getURL(), message);
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
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(new LoadTask(data), new EmptyProgressIndicator());
  }

  @Override
  @NotNull
  protected NodeLoadState getNodeLoadState() {
    return NodeLoadState.REFRESHED;
  }

  private final class LoadTask extends Task.Backgroundable {
    @NotNull private final Pair<RepositoryTreeNode, Expander> myData;
    @NotNull private final List<DirectoryEntry> entries = new ArrayList<>();
    @Nullable private String error;

    private LoadTask(@NotNull Pair<RepositoryTreeNode, Expander> data) {
      super(data.first.getVcs().getProject(), "Loading Child Entries");
      myData = data;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      RepositoryTreeNode node = myData.first;
      SvnVcs vcs = node.getVcs();
      Target target = Target.on(node.getURL());

      try {
        vcs.getFactoryFromSettings().createBrowseClient().list(target, Revision.HEAD, Depth.IMMEDIATES, entries::add);
      }
      catch (VcsException e) {
        error = e.getMessage();
      }
    }

    @Override
    public void onSuccess() {
      if (error != null) {
        setError(myData, error);
      }
      else {
        setResults(myData, sorted(entries, DirectoryEntry.CASE_INSENSITIVE_ORDER));
      }
      startNext();
    }
  }
}
