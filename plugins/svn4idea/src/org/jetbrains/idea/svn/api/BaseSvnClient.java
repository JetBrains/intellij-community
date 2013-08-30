package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

/**
 * @author Konstantin Kolosovsky.
 */
public abstract class BaseSvnClient implements SvnClient {
  protected SvnVcs myVcs;
  protected ClientFactory myFactory;

  @NotNull
  @Override
  public SvnVcs getVcs() {
    return myVcs;
  }

  @Override
  public void setVcs(@NotNull SvnVcs vcs) {
    myVcs = vcs;
  }

  @NotNull
  @Override
  public ClientFactory getFactory() {
    return myFactory;
  }

  @Override
  public void setFactory(@NotNull ClientFactory factory) {
    myFactory = factory;
  }
}
