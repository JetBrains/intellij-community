package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.SvnCommandFactory;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnSimpleCommand;
import org.tmatesoft.svn.core.SVNDepth;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdAddClient extends BaseSvnClient implements AddClient {

  @Override
  public void add(@NotNull File file, SVNDepth depth, boolean makeParents, boolean includeIgnored, boolean force) throws VcsException {
    SvnSimpleCommand command = SvnCommandFactory.createSimpleCommand(myVcs.getProject(), null, SvnCommandName.add);
    command.addParameters(file.getAbsolutePath());
    if (depth != null) {
      command.addParameters("--depth", depth.getName());
    }
    if (makeParents) {
      command.addParameters("--parents");
    }
    if (includeIgnored) {
      command.addParameters("--no-ignore");
    }
    if (force) {
      command.addParameters("--force");
    }
    command.run();
  }
}
