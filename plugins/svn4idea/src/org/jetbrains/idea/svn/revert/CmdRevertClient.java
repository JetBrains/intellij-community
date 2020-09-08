// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.revert;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.Command;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CmdRevertClient extends BaseSvnClient implements RevertClient {

  private static final String STATUS = "\\s*(.+?)\\s*";
  private static final String PATH = "\\s*'(.*?)'\\s*";
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
      Target target = Target.on(Objects.requireNonNull(ContainerUtil.getFirstItem(paths)));
      CommandExecutor executor = execute(myVcs, target, CommandUtil.getHomeDirectory(), command, null);
      FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new RevertStatusConvertor());
      parser.parse(executor.getOutput());
    }
  }

  private static class RevertStatusConvertor implements Convertor<Matcher, ProgressEvent> {

    private static final @NonNls String REVERTED_CODE = "Reverted";
    private static final @NonNls String FAILED_TO_REVERT_CODE = "Failed to revert";
    private static final @NonNls String SKIPPED_CODE = "Skipped";

    @Override
    public ProgressEvent convert(@NotNull Matcher matcher) {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      return createEvent(new File(path), createAction(statusMessage));
    }

    @Nullable
    public static EventAction createAction(@NotNull String code) {
      EventAction result = null;

      if (REVERTED_CODE.equals(code)) {
        result = EventAction.REVERT;
      }
      else if (FAILED_TO_REVERT_CODE.equals(code)) {
        result = EventAction.FAILED_REVERT;
      }
      else if (SKIPPED_CODE.equals(code)) {
        result = EventAction.SKIP;
      }

      return result;
    }
  }
}
