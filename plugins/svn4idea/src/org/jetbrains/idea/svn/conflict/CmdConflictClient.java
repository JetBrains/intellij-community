package org.jetbrains.idea.svn.conflict;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.commandLine.CommandUtil;
import org.jetbrains.idea.svn.commandLine.SvnCommandName;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdConflictClient extends BaseSvnClient implements ConflictClient {

  // TODO: Add possibility to resolve content, property and tree conflicts separately.
  // TODO: Or rewrite logic to have one "Resolve conflicts" action instead of separate actions for each conflict type.
  @Override
  public void resolve(@NotNull File path,
                      @Nullable Depth depth,
                      boolean resolveProperty,
                      boolean resolveContent,
                      boolean resolveTree) throws VcsException {
    List<String> parameters = new ArrayList<>();

    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, depth);
    parameters.add("--accept");
    parameters.add("working");

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.resolve, parameters, null);
  }
}
