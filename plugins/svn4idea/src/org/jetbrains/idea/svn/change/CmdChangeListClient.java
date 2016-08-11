package org.jetbrains.idea.svn.change;

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
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CmdChangeListClient extends BaseSvnClient implements ChangeListClient {

  @Override
  public void add(@NotNull String changeList, @NotNull File path, @Nullable String[] changeListsToOperate) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add(changeList);
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, Depth.EMPTY);
    if (changeListsToOperate != null) {
      CommandUtil.putChangeLists(parameters, Arrays.asList(changeListsToOperate));
    }

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.changelist, parameters, null);
  }

  @Override
  public void remove(@NotNull File path) throws VcsException {
    List<String> parameters = new ArrayList<>();

    parameters.add("--remove");
    CommandUtil.put(parameters, path);
    CommandUtil.put(parameters, Depth.EMPTY);

    // for now parsing of the output is not required as command is executed only for one file
    // and will be either successful or exception will be thrown
    execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.changelist, parameters, null);
  }
}
