package org.jetbrains.idea.svn.lock;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdLockClient extends BaseSvnClient implements LockClient {

  @Override
  public void lock(@NotNull File file, boolean force, @NotNull String message, @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    parameters.add("--message");
    parameters.add(message);

    CommandExecutor command = CommandUtil.execute(myVcs, SvnTarget.fromFile(file), SvnCommandName.lock, parameters, null);
    handleCommandCompletion(command, file, SVNEventAction.LOCKED, SVNEventAction.LOCK_FAILED, handler);
  }

  @Override
  public void unlock(@NotNull File file, boolean force, @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = prepareParameters(file, force);

    CommandExecutor command = CommandUtil.execute(myVcs, SvnTarget.fromFile(file), SvnCommandName.unlock, parameters, null);
    handleCommandCompletion(command, file, SVNEventAction.UNLOCKED, SVNEventAction.UNLOCK_FAILED, handler);
  }

  private static List<String> prepareParameters(@NotNull File file, boolean force) {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, file);
    CommandUtil.put(parameters, force, "--force");

    return parameters;
  }

  private static void handleCommandCompletion(@NotNull CommandExecutor command,
                                              @NotNull File file,
                                              @NotNull SVNEventAction success,
                                              @NotNull SVNEventAction failure,
                                              @Nullable ISVNEventHandler handler) throws VcsException {
    // just warning appears in output when can not lock/unlock file for some reason (like, that file is already locked)
    SVNErrorMessage error = SvnUtil.parseWarning(command.getErrorOutput());

    try {
      invokeHandler(file, error == null ? success : failure, handler, error);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void invokeHandler(@NotNull File file,
                                    @NotNull SVNEventAction action,
                                    @Nullable ISVNEventHandler handler,
                                    @Nullable SVNErrorMessage error)
    throws SVNException {
    if (handler != null) {
      handler.handleEvent(createEvent(file, action, error), 1);
    }
  }

  private static SVNEvent createEvent(@NotNull File file, @NotNull SVNEventAction action, @Nullable SVNErrorMessage error) {
    return new SVNEvent(file, file.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE, null, -1, null, null, null, null, action, action,
                        error, null, null, null, null);
  }
}
