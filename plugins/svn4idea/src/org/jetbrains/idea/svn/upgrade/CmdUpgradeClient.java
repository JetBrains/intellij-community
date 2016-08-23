package org.jetbrains.idea.svn.upgrade;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.LineCommandAdapter;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdUpgradeClient extends BaseSvnClient implements UpgradeClient {

  private static final String STATUS = "\\s*(.+?)\\s*";
  private static final String PATH = "\\s*\'(.*?)\'\\s*";
  private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + PATH);

  @Override
  public void upgrade(@NotNull File path, @NotNull WorkingCopyFormat format, @Nullable ProgressTracker handler) throws VcsException {
    validateFormat(format, getSupportedFormats());

    // fake event indicating upgrade start
    callHandler(handler, createEvent(path, EventAction.UPDATE_COMPLETED));

    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, path);

    // TODO: Add general possibility to invoke "handler.checkCancelled" (process should be killed). But currently upgrade process is not
    // TODO: cancellable from UI - and this makes sense.
    // for 1.8 - no output
    // for 1.7 - output in format "Upgraded '<path>'"
    FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new UpgradeStatusConvertor());
    UpgradeLineCommandListener listener = new UpgradeLineCommandListener(parser);

    execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.upgrade, parameters, listener);
    listener.throwIfException();
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    List<WorkingCopyFormat> result = new ArrayList<>();

    result.add(WorkingCopyFormat.from(myFactory.createVersionClient().getVersion()));

    return result;
  }

  private static class UpgradeStatusConvertor implements Convertor<Matcher, ProgressEvent> {

    public ProgressEvent convert(@NotNull Matcher matcher) {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      return createEvent(new File(path), createAction(statusMessage));
    }

    @Nullable
    public static EventAction createAction(@NotNull String code) {
      EventAction result = null;

      if ("Upgraded".equals(code)) {
        result = EventAction.UPGRADED_PATH;
      }

      return result;
    }
  }

  private static class UpgradeLineCommandListener extends LineCommandAdapter {

    @NotNull private final FileStatusResultParser parser;
    @NotNull private final AtomicReference<VcsException> exception;

    private UpgradeLineCommandListener(@NotNull FileStatusResultParser parser) {
      this.parser = parser;
      exception = new AtomicReference<>();
    }

    @Override
    public void onLineAvailable(String line, Key outputType) {
      if (ProcessOutputTypes.STDOUT.equals(outputType)) {
        try {
          parser.onLine(line);
        }
        catch (VcsException e) {
          exception.set(e);
        }
      }
    }

    public void throwIfException() throws VcsException {
      VcsException e = exception.get();

      if (e != null) {
        throw e;
      }
    }
  }
}
