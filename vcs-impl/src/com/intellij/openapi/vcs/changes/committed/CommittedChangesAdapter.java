package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author yole
 */
public class CommittedChangesAdapter implements CommittedChangesListener {
  public void changesLoaded(RepositoryLocation location, List<CommittedChangeList> changes) {
  }

  public void incomingChangesUpdated(@Nullable final List<CommittedChangeList> receivedChanges) {
  }

  public void refreshErrorStatusChanged(@Nullable VcsException lastError) {
  }
}