package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnCommitRunner;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.ISVNCommitHandler;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdImportClient extends BaseSvnClient implements ImportClient {

  @Override
  public long doImport(@NotNull File path,
                       @NotNull SVNURL url,
                       @Nullable SVNDepth depth,
                       @NotNull String message,
                       boolean noIgnore,
                       @Nullable CommitEventHandler handler,
                       @Nullable ISVNCommitHandler commitHandler) throws VcsException {
    // TODO: ISVNFileFilter from ISVNCommitHandler is not currently implemented

    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, path, false);
    CommandUtil.put(parameters, SvnTarget.fromURL(url), false);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, noIgnore, "--no-ignore");
    parameters.add("--message");
    parameters.add(message);

    SvnCommitRunner.CommandListener listener = new SvnCommitRunner.CommandListener(handler);
    listener.setBaseDirectory(path);

    CommandUtil.execute(myVcs, SvnTarget.fromURL(url), SvnCommandName.importFolder, parameters, listener);

    return listener.getCommittedRevision();
  }
}
