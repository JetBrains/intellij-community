package org.jetbrains.idea.svn.cleanup;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdCleanupClient extends BaseSvnClient implements CleanupClient {

  @Override
  public void cleanup(@NotNull File path, @Nullable ProgressTracker handler) throws VcsException {
    // TODO: Implement event handler support - currently in SVNKit implementation handler is used to support cancelling
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, path);
    execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.cleanup, parameters, null);
  }
}
