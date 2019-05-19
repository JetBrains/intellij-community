// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;

import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

public class CmdHistoryClient extends BaseSvnClient implements HistoryClient {

  @Override
  public void doLog(@NotNull Target target,
                    @NotNull Revision startRevision,
                    @NotNull Revision endRevision,
                    boolean stopOnCopy,
                    boolean discoverChangedPaths,
                    boolean includeMergedRevisions,
                    long limit,
                    @Nullable String[] revisionProperties,
                    @Nullable LogEntryConsumer handler) throws VcsException {
    // TODO: add revision properties parameter if necessary

    List<String> parameters =
      prepareCommand(target, startRevision, endRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit);

    CommandExecutor command = execute(myVcs, target, SvnCommandName.log, parameters, null);
    // TODO: handler should be called in parallel with command execution, but this will be in other thread
    // TODO: check if that is ok for current handler implementation
    parseOutput(command, handler);
  }

  private static void parseOutput(@NotNull CommandExecutor command, @Nullable LogEntryConsumer handler) throws VcsException {
    try {
      LogInfo log = CommandUtil.parse(command.getOutput(), LogInfo.class);

      if (handler != null && log != null) {
        for (LogEntry.Builder entry : log.entries) {
          iterateRecursively(entry, handler);
        }
      }
    }
    catch (JAXBException e) {
      throw new VcsException(e);
    }
  }

  private static void iterateRecursively(@NotNull LogEntry.Builder entry, @NotNull LogEntryConsumer handler) throws SvnBindException {
    handler.consume(entry.build());

    for (LogEntry.Builder childEntry : entry.getChildEntries()) {
      iterateRecursively(childEntry, handler);
    }

    if (entry.hasChildren()) {
      // empty log entry passed to handler to fully correspond to SVNKit behavior.
      handler.consume(LogEntry.EMPTY);
    }
  }

  private static List<String> prepareCommand(@NotNull Target target,
                                             @NotNull Revision startRevision,
                                             @NotNull Revision endRevision,
                                             boolean stopOnCopy, boolean discoverChangedPaths, boolean includeMergedRevisions, long limit) {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, target);
    CommandUtil.put(parameters, startRevision, endRevision);
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
    public List<LogEntry.Builder> entries = new ArrayList<>();
  }
}
