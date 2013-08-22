package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.AddClient;
import org.jetbrains.idea.svn.conflict.ConflictClient;
import org.jetbrains.idea.svn.copy.CopyMoveClient;
import org.jetbrains.idea.svn.delete.DeleteClient;
import org.jetbrains.idea.svn.history.HistoryClient;
import org.jetbrains.idea.svn.portable.SvnStatusClientI;
import org.jetbrains.idea.svn.revert.RevertClient;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class ClientFactory {

  @NotNull
  protected SvnVcs myVcs;

  protected AddClient addClient;
  protected HistoryClient historyClient;
  protected RevertClient revertClient;
  protected DeleteClient deleteClient;
  protected SvnStatusClientI statusClient;
  protected CopyMoveClient copyMoveClient;
  protected ConflictClient conflictClient;

  protected ClientFactory(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  @NotNull
  public AddClient createAddClient() {
    return prepare(addClient);
  }

  @NotNull
  public HistoryClient createHistoryClient() {
    return prepare(historyClient);
  }

  @NotNull
  public RevertClient createRevertClient() {
    return prepare(revertClient);
  }

  @NotNull
  public SvnStatusClientI createStatusClient() {
    // TODO: Update this in same like other clients - move to corresponding package, rename clients
    return statusClient;
  }

  @NotNull
  public DeleteClient createDeleteClient() {
    return prepare(deleteClient);
  }

  @NotNull
  public CopyMoveClient createCopyMoveClient() {
    return prepare(copyMoveClient);
  }

  @NotNull
  public ConflictClient createConflictClient() {
    return prepare(conflictClient);
  }

  @NotNull
  protected <T extends SvnClient> T prepare(@NotNull T client) {
    client.setVcs(myVcs);

    return client;
  }
}
