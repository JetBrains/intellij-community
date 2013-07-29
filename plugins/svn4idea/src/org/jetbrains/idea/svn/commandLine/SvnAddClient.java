package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNDepth;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnAddClient {
  private Project myProject;

  public SvnAddClient(@NotNull Project project) {
    myProject = project;
  }

  public void add(@NotNull File file, SVNDepth depth, boolean makeParents, boolean includeIgnored, boolean force) throws VcsException {
    SvnSimpleCommand command = SvnCommandFactory.createSimpleCommand(myProject, null, SvnCommandName.add);
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
