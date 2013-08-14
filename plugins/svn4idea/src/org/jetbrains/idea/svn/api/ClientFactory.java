package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.AddClient;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class ClientFactory {

  @NotNull
  protected SvnVcs myVcs;

  protected AddClient addClient;

  protected ClientFactory(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    setup();
  }

  protected abstract void setup();

  public AddClient createAddClient() {
    return prepare(addClient);
  }

  protected <T extends SvnClient> T prepare(T client) {
    client.setVcs(myVcs);

    return client;
  }
}
