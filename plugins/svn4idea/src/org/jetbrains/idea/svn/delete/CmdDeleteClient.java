package org.jetbrains.idea.svn.delete;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdDeleteClient extends BaseSvnClient implements DeleteClient {

  @Override
  public void delete(@NotNull File path, boolean force, boolean dryRun, @Nullable ProgressTracker handler) throws VcsException {
    // TODO: no actual support for dryRun in 'svn delete', SvnKit performs certain validation on file status and svn:externals property
    // TODO: probably add some widespread checks for dryRun delete - but most likely this should be placed upper - in merge logic
    if (!dryRun) {
      List<String> parameters = new ArrayList<>();

      CommandUtil.put(parameters, path);
      CommandUtil.put(parameters, force, "--force");

      File workingDirectory = CommandUtil.getHomeDirectory();
      BaseUpdateCommandListener listener = new BaseUpdateCommandListener(workingDirectory, handler);

      execute(myVcs, SvnTarget.fromFile(path), workingDirectory, SvnCommandName.delete, parameters, listener);

      listener.throwWrappedIfException();
    }
  }

  @Override
  public long delete(@NotNull SVNURL url, @NotNull String message) throws VcsException {
    SvnTarget target = SvnTarget.fromURL(url);
    List<String> parameters = ContainerUtil.newArrayList();

    CommandUtil.put(parameters, target);
    parameters.add("--message");
    parameters.add(message);

    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(null);

    execute(myVcs, target, SvnCommandName.delete, parameters, listener);

    return listener.getCommittedRevision();
  }
}
