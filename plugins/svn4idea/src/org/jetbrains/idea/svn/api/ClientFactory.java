package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.AddClient;
import org.jetbrains.idea.svn.history.HistoryClient;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class ClientFactory {

  @NotNull
  protected SvnVcs myVcs;

  protected AddClient addClient;
  protected HistoryClient historyClient;

  protected ClientFactory(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  public AddClient createAddClient() {
    return prepare(addClient);
  }

  public HistoryClient createHistoryClient() {
    return prepare(historyClient);
  }

  protected <T extends SvnClient> T prepare(T client) {
    client.setVcs(myVcs);

    return client;
  }
}
