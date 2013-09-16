package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.LineSeparator;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommand;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdHistoryClient extends BaseSvnClient implements HistoryClient {

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.svn.history.CmdHistoryClient");

  @Override
  public void doLog(@NotNull File path,
                    @NotNull SVNRevision startRevision,
                    @NotNull SVNRevision endRevision,
                    @Nullable SVNRevision pegRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable ISVNLogEntryHandler handler) throws VcsException {
    // TODO: add revision properties parameter if necessary
    // TODO: svn log command supports --xml option - could update parsing to use xml format

    // TODO: after merge remove setting includeMergedRevisions to false and update parsing
    includeMergedRevisions = false;

    List<String> parameters =
      prepareCommand(path, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit);

    try {
      SvnCommand command = CommandUtil.execute(myVcs, SvnTarget.fromFile(path, pegRevision), SvnCommandName.log, parameters, null);
      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      parseOutput(handler, command);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void parseOutput(@Nullable ISVNLogEntryHandler handler, @NotNull SvnCommand command)
    throws VcsException, SVNException {
    Parser parser = new Parser(handler);
    for (String line : StringUtil.splitByLines(command.getOutput(), false)) {
      parser.onLine(line);
    }
  }

  private static List<String> prepareCommand(@NotNull File path,
                                             @NotNull SVNRevision startRevision,
                                             @NotNull SVNRevision endRevision,
                                             @Nullable SVNRevision pegRevision,
                                             boolean stopOnCopy, boolean discoverChangedPaths, boolean includeMergedRevisions, long limit) {
    List<String> parameters = new ArrayList<String>();

    CommandUtil.put(parameters, path, pegRevision);
    parameters.add("--revision");
    parameters.add(startRevision + ":" + endRevision);

    CommandUtil.put(parameters, stopOnCopy, "--stop-on-copy");
    CommandUtil.put(parameters, discoverChangedPaths, "--verbose");
    CommandUtil.put(parameters, includeMergedRevisions, "--use-merge-history");
    if (limit > 0) {
      parameters.add("--limit");
      parameters.add(String.valueOf(limit));
    }

    return parameters;
  }

  private static class Parser {
    private static final String REVISION = "\\s*r(\\d+)\\s*";
    private static final String AUTHOR = "\\s*([^|]*)\\s*";
    private static final String DATE = "\\s*([^|]*)\\s*";
    private static final String MESSAGE_LINES = "\\s*(\\d+).*";

    private static final Pattern ENTRY_START = Pattern.compile("-+");
    private static final Pattern DETAILS = Pattern.compile(REVISION + "\\|" + AUTHOR + "\\|" + DATE + "\\|" + MESSAGE_LINES);

    private static final String STATUS = "\\s*(\\w)";
    private static final String PATH = "\\s*(.*?)\\s*";
    private static final String COPY_FROM_PATH = "(/[^:]*)";
    private static final String COPY_FROM_REVISION = "(\\d+)\\))\\s*";
    private static final String COPY_FROM_INFO = "((\\(from " + COPY_FROM_PATH + ":" + COPY_FROM_REVISION + ")?";
    private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + PATH + COPY_FROM_INFO);

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    ISVNLogEntryHandler handler;

    Entry entry;
    boolean waitDetails;
    boolean waitChangedPath;
    boolean waitMessage;

    public Parser(@Nullable ISVNLogEntryHandler handler) {
      this.handler = handler;
    }

    public void onLine(@NotNull String line) throws VcsException, SVNException {
      if (ENTRY_START.matcher(line).matches()) {
        processEntryStart();
      }
      else if (waitDetails) {
        processDetails(line);
      }
      else if (waitMessage) {
        processMessage(line);
      }
      else if (StringUtil.isEmpty(line.trim())) {
        processChangedPathsFinished();
      }
      else if (line.startsWith("Changed paths:")) {
        processChangedPathsStarted();
      }
      else if (waitChangedPath) {
        processChangedPath(line);
      }
      else {
        throw new VcsException("unknown state on line " + line);
      }
    }

    private void processChangedPath(@NotNull String line) throws VcsException {
      Matcher matcher = CHANGED_PATH.matcher(line);
      if (!matcher.matches()) {
        throw new VcsException("changed path not found in " + line);
      }

      String path = matcher.group(2);
      char type = CommandUtil.getStatusChar(matcher.group(1));
      String copyPath = matcher.group(5);
      long copyRevision = !StringUtil.isEmpty(matcher.group(6)) ? Long.valueOf(matcher.group(6)) : 0;

      entry.changedPaths.put(path, new SVNLogEntryPath(path, type, copyPath, copyRevision));
    }

    private void processChangedPathsStarted() {
      waitChangedPath = true;
    }

    private void processChangedPathsFinished() {
      waitChangedPath = false;
      waitMessage = true;
    }

    private void processMessage(@NotNull String line) {
      entry.message.append(line);
      entry.message.append(LineSeparator.LF.getSeparatorString());
    }

    private void processDetails(@NotNull String line) throws VcsException {
      Matcher matcher = DETAILS.matcher(line);
      if (!matcher.matches()) {
        throw new VcsException("details not found in " + line);
      }
      entry.revision = Long.valueOf(matcher.group(1));
      entry.author = matcher.group(2).trim();
      entry.date = tryGetDate(matcher.group(3));

      waitDetails = false;
    }

    private void processEntryStart() throws SVNException {
      if (entry != null) {
        handler.handleLogEntry(entry.toLogEntry());
      }
      entry = new Entry();
      waitDetails = true;
      waitMessage = false;
    }

    private static Date tryGetDate(@NotNull String value) {
      Date result = null;
      try {
        result = DATE_FORMAT.parse(value);
      }
      catch (ParseException e) {
        LOG.debug(e);
      }
      return result;
    }

    private static class Entry {
      Map<String, SVNLogEntryPath> changedPaths = new HashMap<String, SVNLogEntryPath>();
      long revision;
      String author;
      Date date;
      StringBuilder message = new StringBuilder();

      private SVNLogEntry toLogEntry() {
        return new SVNLogEntry(changedPaths, revision, author, date, message.toString());
      }
    }
  }
}
