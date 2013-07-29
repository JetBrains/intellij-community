package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnCatClient {

  private Project myProject;

  public SvnCatClient(@NotNull Project project) {
    myProject = project;
  }

  public String getFileContents(@NotNull File file) throws VcsException {
    SvnSimpleCommand command = SvnCommandFactory.createSimpleCommand(myProject, null, SvnCommandName.cat);
    command.addParameters(file.getAbsolutePath());
    return command.run();
  }
}
