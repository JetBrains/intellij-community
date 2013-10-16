package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdHistoryClient extends BaseSvnClient implements HistoryClient {

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

    List<String> parameters =
      prepareCommand(path, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit);

    try {
      CommandExecutor command = CommandUtil.execute(myVcs, SvnTarget.fromFile(path, pegRevision), SvnCommandName.log, parameters, null);
      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      parseOutput(command, handler);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private static void parseOutput(@NotNull CommandExecutor command, @Nullable ISVNLogEntryHandler handler)
    throws VcsException, SVNException {
    try {
      LogInfo log = CommandUtil.parse(command.getOutput(), LogInfo.class);

      if (handler != null && log != null) {
        for (LogEntry entry : log.entries) {
          iterateRecursively(entry, handler);
        }
      }
    }
    catch (JAXBException e) {
      throw new VcsException(e);
    }
  }

  private static void iterateRecursively(@NotNull LogEntry entry, @NotNull ISVNLogEntryHandler handler) throws SVNException {
    handler.handleLogEntry(entry.toLogEntry());

    for (LogEntry childEntry : entry.childEntries) {
      iterateRecursively(childEntry, handler);
    }

    if (entry.hasChildren()) {
      // empty log entry passed to handler to fully correspond to SVNKit behavior.
      handler.handleLogEntry(SVNLogEntry.EMPTY_ENTRY);
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
    parameters.add("--xml");

    return parameters;
  }

  @XmlRootElement(name = "log")
  public static class LogInfo {

    @XmlElement(name = "logentry")
    public List<LogEntry> entries = new ArrayList<LogEntry>();
  }

  public static class LogEntry {

    @XmlAttribute(name = "revision")
    public long revision;

    @XmlElement(name = "author")
    public String author;

    @XmlElement(name = "date")
    public Date date;

    @XmlElement(name = "msg")
    public String message;

    @XmlElement(name = "paths")
    public ChangedPaths changedPaths;

    @XmlElement(name = "logentry")
    public List<LogEntry> childEntries = new ArrayList<LogEntry>();

    public boolean hasChildren() {
      return !childEntries.isEmpty();
    }

    public SVNLogEntry toLogEntry() {
      SVNLogEntry entry = new SVNLogEntry(toChangedPathsMap(), revision, author, date, message);

      entry.setHasChildren(hasChildren());

      return entry;
    }

    public Map<String, SVNLogEntryPath> toChangedPathsMap() {
      return changedPaths != null ? changedPaths.toMap() : ContainerUtil.<String, SVNLogEntryPath>newHashMap();
    }
  }

  public static class ChangedPaths {

    @XmlElement(name = "path")
    public List<ChangedPath> changedPaths = new ArrayList<ChangedPath>();

    public Map<String, SVNLogEntryPath> toMap() {
      Map<String, SVNLogEntryPath> changes = ContainerUtil.newHashMap();

      for (ChangedPath path : changedPaths) {
        changes.put(path.path, path.toLogEntryPath());
      }

      return changes;
    }
  }

  public static class ChangedPath {

    @XmlAttribute(name = "kind")
    public String kind;

    @XmlAttribute(name = "action")
    public String action;

    @XmlAttribute(name = "copyfrom-path")
    public String copyFromPath;

    @XmlAttribute(name = "copyfrom-rev")
    public long copyFromRevision;

    @XmlValue
    public String path;

    public SVNLogEntryPath toLogEntryPath() {
      return new SVNLogEntryPath(path, CommandUtil.getStatusChar(action), copyFromPath, copyFromRevision);
    }
  }
}
