package org.jetbrains.idea.svn.add;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.commandLine.SvnLineCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdAddClient extends BaseSvnClient implements AddClient {

  private static final double DEFAULT_PROGRESS = 0.0;

  @Override
  public void add(@NotNull File file,
                  @Nullable SVNDepth depth,
                  boolean makeParents,
                  boolean includeIgnored,
                  boolean force,
                  @Nullable ISVNEventHandler handler) throws VcsException {
    List<String> parameters = prepareParameters(file, depth, makeParents, includeIgnored, force);

    try {
      SvnLineCommand command = CommandUtil.runSimple(SvnCommandName.add, myVcs, null, null, parameters);
      // TODO: handler should be called in parallel with command execution, but this will be in other thread
      // TODO: check if that is ok for current handler implementation
      // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed

      parseOutput(command, handler);
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }

  private void parseOutput(@NotNull SvnLineCommand command, @Nullable ISVNEventHandler handler) throws VcsException, SVNException {
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

  private static List<String> prepareParameters(File file, SVNDepth depth, boolean makeParents, boolean includeIgnored, boolean force) {
    List<String> parameters = new ArrayList<String>();

    parameters.add(file.getAbsolutePath());
    if (depth != null) {
      parameters.add("--depth");
      parameters.add(depth.getName());
    }
    CommandUtil.put(parameters, makeParents, "--parents");
    CommandUtil.put(parameters, includeIgnored, "--no-ignore");
    CommandUtil.put(parameters, force, "--force");

    return parameters;
  }

  private static class Parser {
    private static final String STATUS = "\\s*(\\w)\\s*";
    private static final String OPTIONAL_FILE_TYPE = "(\\(.*\\))?";
    private static final String PATH = "\\s*(.*?)\\s*";
    private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + OPTIONAL_FILE_TYPE + PATH);

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
      SVNStatusType contentStatus = CommandUtil.getStatusType(matcher.group(1));
      String path = matcher.group(3);

      if (handler != null) {
        // TODO: No suitable ways to create SVNEvent. This will be changed when removing SvnKit objects from interfaces.
        handler.handleEvent(
          new SVNEvent(new File(path), null, null, 0, contentStatus, null, null, null, null, null, null, null, null, null, null),
          DEFAULT_PROGRESS);
      }
    }
  }
}
