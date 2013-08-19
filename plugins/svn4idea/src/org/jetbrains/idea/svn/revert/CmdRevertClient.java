package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnLineCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdRevertClient extends BaseSvnClient implements RevertClient {

  private static final double DEFAULT_PROGRESS = 0.0;

  @Override
  public void revert(@NotNull File[] paths, @Nullable SVNDepth depth, @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = prepareParameters(paths, depth);

    try {
      SvnLineCommand command = CommandUtil.runSimple(SvnCommandName.revert, myVcs, null, null, parameters);
      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed

      parseOutput(command, handler);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void parseOutput(@NotNull SvnCommand command, @Nullable ISVNEventHandler handler) throws VcsException, SVNException {
    if (StringUtil.isEmpty(command.getOutput())) {
      // if file is versioned, adding it with "force" produces no output
      // TODO: so handler will not be called - check if this compatible with svnkit
      return;
    }

    Parser parser = new Parser(handler);
    for (String line : StringUtil.splitByLines(command.getOutput())) {
      parser.onLine(line);
    }
  }

  private static List<String> prepareParameters(File[] paths, SVNDepth depth) {
    ArrayList<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, paths);
    CommandUtil.put(parameters, depth);

    return parameters;
  }

  private static class Parser {
    private static final String STATUS = "\\s*(.+?)\\s*";
    private static final String PATH = "\\s*\'(.*?)\'\\s*";
    private static final String OPTIONAL_COMMENT = "(.*)";
    private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + PATH + OPTIONAL_COMMENT);

    @Nullable
    ISVNEventHandler handler;

    public Parser(@Nullable ISVNEventHandler handler) {
      this.handler = handler;
    }

    public void onLine(@NotNull String line) throws VcsException, SVNException {
      Matcher matcher = CHANGED_PATH.matcher(line);
      if (matcher.matches()) {
        processChangedPath(matcher);
      }
      else {
        throw new VcsException("unknown state on line " + line);
      }
    }

    private void processChangedPath(@NotNull Matcher matcher) throws VcsException, SVNException {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      if (handler != null) {
        // TODO: No suitable ways to create SVNEvent. This will be changed when removing SvnKit objects from interfaces.
        handler.handleEvent(
          new SVNEvent(new File(path), null, null, 0, null, null, null, null, createAction(statusMessage), null, null, null, null, null,
                       null),
          DEFAULT_PROGRESS);
      }
    }

    @Nullable
    public static SVNEventAction createAction(@NotNull String code) {
      SVNEventAction result = null;

      if ("Reverted".equals(code)) {
        result = SVNEventAction.REVERT;
      }
      else if ("Failed to revert".equals(code)) {
        result = SVNEventAction.FAILED_REVERT;
      }
      else if ("Skipped".equals(code)) {
        result = SVNEventAction.SKIP;
      }

      return result;
    }
  }
}
