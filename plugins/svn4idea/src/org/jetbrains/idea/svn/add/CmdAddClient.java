package org.jetbrains.idea.svn.add;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.*;
import org.jetbrains.idea.svn.commandLine.CommandExecutor;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.status.StatusType;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdAddClient extends BaseSvnClient implements AddClient {

  private static final String STATUS = "\\s*(\\w)\\s*";
  private static final String OPTIONAL_FILE_TYPE = "(\\(.*\\))?";
  private static final String PATH = "\\s*(.*?)\\s*";
  private static final Pattern CHANGED_PATH = Pattern.compile(STATUS + OPTIONAL_FILE_TYPE + PATH);

  @Override
  public void add(@NotNull File file,
                  @Nullable Depth depth,
                  boolean makeParents,
                  boolean includeIgnored,
                  boolean force,
                  @Nullable ProgressTracker handler) throws VcsException {
    List<String> parameters = prepareParameters(file, depth, makeParents, includeIgnored, force);

    // TODO: handler should be called in parallel with command execution, but this will be in other thread
    // TODO: check if that is ok for current handler implementation
    // TODO: add possibility to invoke "handler.checkCancelled" - process should be killed
    CommandExecutor command = execute(myVcs, SvnTarget.fromFile(file), SvnCommandName.add, parameters, null);
    FileStatusResultParser parser = new FileStatusResultParser(CHANGED_PATH, handler, new AddStatusConvertor());
    parser.parse(command.getOutput());
  }

  private static List<String> prepareParameters(File file, Depth depth, boolean makeParents, boolean includeIgnored, boolean force) {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, file);
    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, makeParents, "--parents");
    CommandUtil.put(parameters, includeIgnored, "--no-ignore");
    CommandUtil.put(parameters, force, "--force");

    return parameters;
  }

  private static class AddStatusConvertor implements Convertor<Matcher, ProgressEvent> {
    @Override
    public ProgressEvent convert(Matcher o) {
      StatusType contentStatus = CommandUtil.getStatusType(o.group(1));
      String path = o.group(3);

      return new ProgressEvent(new File(path), 0, contentStatus, null, null, null, null);
    }
  }
}
