package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnCatClient {

  private Project myProject;

  public SvnCatClient(@NotNull Project project) {
    myProject = project;
  }

  public String getFileContents(@NotNull String path, SVNRevision pegRevision, SVNRevision revision) throws VcsException {
    // TODO: rewrite this to provide output as Stream
    SvnSimpleCommand command = SvnCommandFactory.createSimpleCommand(myProject, null, SvnCommandName.cat);
    fillParameters(path, pegRevision, revision, command);

    try {
      return CommandUtil.runSimple(command, SvnVcs.getInstance(myProject), null).getOutput();
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private void fillParameters(String path, SVNRevision pegRevision, SVNRevision revision, SvnSimpleCommand command) {
    if (revision != null && ! SVNRevision.UNDEFINED.equals(revision) && ! SVNRevision.WORKING.equals(revision)) {
      command.addParameters("-r");
      command.addParameters(revision.toString());
    }
    if (pegRevision != null && ! SVNRevision.UNDEFINED.equals(pegRevision) && ! SVNRevision.WORKING.equals(pegRevision)) {
      command.addParameters(path + "@" + pegRevision.toString());
    } else {
      command.addParameters(path);
    }
  }
}
