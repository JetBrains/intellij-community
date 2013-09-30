package org.jetbrains.idea.svn.upgrade;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.FileStatusResultParser;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
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
public class CmdUpgradeClient extends BaseSvnClient implements UpgradeClient {

  private static final String STATUS = "\\s*(.+?)\\s*";
  private static final String PATH = "\\s*\'(.*?)\'\\s*";
  private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + PATH);

  @Override
  public void upgrade(@NotNull File path, @NotNull WorkingCopyFormat format, @Nullable ISVNEventHandler handler) throws VcsException {
    validateFormat(format, getSupportedFormats());

    // fake event indicating upgrade start
    callHandler(handler, createEvent(path, SVNEventAction.UPDATE_COMPLETED));

    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, path);

    // TODO: handler should be called in parallel with command execution, but this will be in other thread
    // TODO: check if that is ok for current handler implementation
    // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed
    // for 1.8 - no output
    // for 1.7 - output in format "Upgraded '<path>'"
    SvnCommand command = CommandUtil.execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.upgrade, parameters, null);
    FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new UpgradeStatusConvertor());
    parser.parse(command.getOutput());
  }

  @Override
  public List<WorkingCopyFormat> getSupportedFormats() throws VcsException {
    List<WorkingCopyFormat> result = new ArrayList<WorkingCopyFormat>();

    result.add(WorkingCopyFormat.from(myFactory.createVersionClient().getVersion()));

    return result;
  }

  private static class UpgradeStatusConvertor implements Convertor<Matcher, SVNEvent> {

    public SVNEvent convert(@NotNull Matcher matcher) {
      String statusMessage = matcher.group(1);
      String path = matcher.group(2);

      return createEvent(new File(path), createAction(statusMessage));
    }

    @Nullable
    public static SVNEventAction createAction(@NotNull String code) {
      SVNEventAction result = null;

      if ("Upgraded".equals(code)) {
        result = SVNEventAction.UPGRADED_PATH;
      }

      return result;
    }
  }
}
