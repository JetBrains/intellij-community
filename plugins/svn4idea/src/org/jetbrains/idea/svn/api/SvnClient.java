package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

/**
 * @author Konstantin Kolosovsky.
 */
public interface SvnClient {

  @NotNull
  SvnVcs getVcs();

  @NotNull
  ClientFactory getFactory();

  void setVcs(@NotNull SvnVcs vcs);

  void setFactory(@NotNull ClientFactory factory);

  void setIsActive(boolean isActive);
}
