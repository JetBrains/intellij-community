package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdCopyMoveClient extends BaseSvnClient implements CopyMoveClient {

  @Override
  public void copy(@NotNull File src, @NotNull File dst, boolean makeParents, boolean isMove) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, src);
    CommandUtil.put(parameters, dst, false);
    CommandUtil.put(parameters, makeParents, "--parents");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    // TODO: for now use dst as target, as if using source - then process will be started in the source folder and folder will be locked
    // TODO: And if resolving working directory to some other folder (i.e. project root) => errors in parsing (info, status) occur, as
    // TODO: base directory passed to parser do not correspond to working directory, but svn outputs relative paths
    CommandUtil.execute(myVcs, SvnTarget.fromFile(dst), isMove ? SvnCommandName.move : SvnCommandName.copy, parameters, null);
  }

  @Override
  public long copy(@NotNull SvnTarget source,
                            @NotNull SvnTarget destination,
                            @Nullable SVNRevision revision,
                            boolean makeParents,
                            @NotNull String message,
                            @Nullable CommitEventHandler handler) throws VcsException {
    if (!destination.isURL()) {
      throw new IllegalArgumentException("Only urls are supported as destination " + destination);
    }

    // TODO: Check that command fails when destination exists
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");
    parameters.add("--message");
    parameters.add(message);

    // copy to url output is the same as commit output - just statuses have "copy of" suffix
    // so "Adding" will be "Adding copy of"
    SvnCommitRunner.CommandListener listener = new SvnCommitRunner.CommandListener(handler);
    // TODO: Check correctness when source is url
    if (source.isFile()) {
      listener.setBaseDirectory(source.getFile());
    }
    CommandUtil.execute(myVcs, source, SvnCommandName.copy, parameters, listener);

    return listener.getCommittedRevision();
  }

  @Override
  public void copy(@NotNull SvnTarget source,
                   @NotNull File destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");

    File workingDirectory = CommandUtil.getHomeDirectory();
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(workingDirectory, handler);

    CommandUtil.execute(myVcs, source, workingDirectory, SvnCommandName.copy, parameters, listener);

    listener.throwWrappedIfException();
  }
}
