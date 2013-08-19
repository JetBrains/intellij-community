package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.add.CmdAddClient;
import org.jetbrains.idea.svn.commandLine.SvnCommandLineStatusClient;
import org.jetbrains.idea.svn.delete.CmdDeleteClient;
import org.jetbrains.idea.svn.history.CmdHistoryClient;
import org.jetbrains.idea.svn.revert.CmdRevertClient;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdClientFactory extends ClientFactory {

  public CmdClientFactory(@NotNull SvnVcs vcs) {
    super(vcs);
  }

  @Override
  protected void setup() {
    addClient = new CmdAddClient();
    historyClient = new CmdHistoryClient();
    revertClient = new CmdRevertClient();
    deleteClient = new CmdDeleteClient();
    statusClient = new SvnCommandLineStatusClient(myVcs.getProject());
  }
}
