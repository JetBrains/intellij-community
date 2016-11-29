package org.jetbrains.idea.svn.copy;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.jetbrains.idea.svn.checkin.CmdCheckinClient;
import org.jetbrains.idea.svn.checkin.CommitEventHandler;
import org.jetbrains.idea.svn.commandLine.BaseUpdateCommandListener;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
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
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, src);
    CommandUtil.put(parameters, dst, false);
    CommandUtil.put(parameters, makeParents, "--parents");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    // Use idea home directory for directory renames which differ only by character case on case insensitive file systems - otherwise that
    // directory being renamed will be blocked by svn process
    File workingDirectory =
      isMove && !SystemInfo.isFileSystemCaseSensitive && FileUtil.filesEqual(src, dst) ? CommandUtil.getHomeDirectory() : null;
    execute(myVcs, SvnTarget.fromFile(dst), workingDirectory, getCommandName(isMove), parameters, null);
  }

  @Override
  public long copy(@NotNull SvnTarget source,
                   @NotNull SvnTarget destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   boolean isMove,
                   @NotNull String message,
                   @Nullable CommitEventHandler handler) throws VcsException {
    if (!destination.isURL()) {
      throw new IllegalArgumentException("Only urls are supported as destination " + destination);
    }

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");
    parameters.add("--message");
    parameters.add(message);

    // copy to url output is the same as commit output - just statuses have "copy of" suffix
    // so "Adding" will be "Adding copy of"
    CmdCheckinClient.CommandListener listener = new CmdCheckinClient.CommandListener(handler);
    if (source.isFile()) {
      listener.setBaseDirectory(source.getFile());
    }
    execute(myVcs, source, getCommandName(isMove), parameters, listener);

    return listener.getCommittedRevision();
  }

  @Override
  public void copy(@NotNull SvnTarget source,
                   @NotNull File destination,
                   @Nullable SVNRevision revision,
                   boolean makeParents,
                   @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, source);
    CommandUtil.put(parameters, destination);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, makeParents, "--parents");

    File workingDirectory = CommandUtil.getHomeDirectory();
    BaseUpdateCommandListener listener = new BaseUpdateCommandListener(workingDirectory, handler);

    execute(myVcs, source, workingDirectory, SvnCommandName.copy, parameters, listener);

    listener.throwWrappedIfException();
  }

  @NotNull
  private static SvnCommandName getCommandName(boolean isMove) {
    return isMove ? SvnCommandName.move : SvnCommandName.copy;
  }
}
