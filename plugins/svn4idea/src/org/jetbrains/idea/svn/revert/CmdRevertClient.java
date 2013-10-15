package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.FileStatusResultParser;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdRevertClient extends BaseSvnClient implements RevertClient {

  private static final String STATUS = "\\s*(.+?)\\s*";
  private static final String PATH = "\\s*\'(.*?)\'\\s*";
  private static final String OPTIONAL_COMMENT = "(.*)";
  private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + PATH + OPTIONAL_COMMENT);

  @Override
  public void revert(@NotNull File[] paths, @Nullable SVNDepth depth, @Nullable ISVNEventHandler handler) throws VcsException {
    if (paths.length > 0) {
      List<String> parameters = prepareParameters(paths, depth);

      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed
      CommandExecutor command = CommandUtil.execute(myVcs, SvnTarget.fromFile(paths[0]), SvnCommandName.revert, parameters, null);
      FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new RevertStatusConvertor());
      parser.parse(command.getOutput());
    }
  }

  private static List<String> prepareParameters(File[] paths, SVNDepth depth) {
    ArrayList<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, paths);
    CommandUtil.put(parameters, depth);

    return parameters;
  }

  private static class RevertStatusConvertor implements Convertor<Matcher, SVNEvent> {

    public SVNEvent convert(@NotNull Matcher matcher) {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      return createEvent(new File(path), createAction(statusMessage));
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
