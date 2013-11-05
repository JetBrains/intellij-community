package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, force, "--force");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    CommandUtil.execute(myVcs, SvnTarget.fromFile(path), CommandUtil.getHomeDirectory(), SvnCommandName.delete, parameters, null);
  }
}
