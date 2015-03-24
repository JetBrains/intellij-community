package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.Command;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.Collection;
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
  public void revert(@NotNull Collection<File> paths, @Nullable Depth depth, @Nullable ProgressTracker handler) throws VcsException {
    if (!ContainerUtil.isEmpty(paths)) {
      Command command = newCommand(SvnCommandName.revert);

      command.put(depth);
      command.setTargets(paths);

      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed
      SvnTarget target = SvnTarget.fromFile(ObjectUtils.assertNotNull(ContainerUtil.getFirstItem(paths)));
      CommandExecutor executor = execute(myVcs, target, CommandUtil.getHomeDirectory(), command, null);
      FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new RevertStatusConvertor());
      parser.parse(executor.getOutput());
    }
  }

  private static class RevertStatusConvertor implements Convertor<Matcher, ProgressEvent> {

    public ProgressEvent convert(@NotNull Matcher matcher) {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      return createEvent(new File(path), createAction(statusMessage));
    }

    @Nullable
    public static EventAction createAction(@NotNull String code) {
      EventAction result = null;

      if ("Reverted".equals(code)) {
        result = EventAction.REVERT;
      }
      else if ("Failed to revert".equals(code)) {
        result = EventAction.FAILED_REVERT;
      }
      else if ("Skipped".equals(code)) {
        result = EventAction.SKIP;
      }

      return result;
    }
  }
}
