package org.jetbrains.idea.svn.commandLine;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnCommitRunner;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.jetbrains.idea.svn.config.SvnBindException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandUtil {
  public static SvnLineCommand runSimple(SvnCommandName name,
                                         SvnVcs vcs,
                                         @Nullable SVNURL url,
                                         List<String> parameters)
    throws SVNException {
    String exe = SvnApplicationSettings.getInstance().getCommandLinePath();

    try {
      return SvnLineCommand
        .runWithAuthenticationAttempt(exe, new File(vcs.getProject().getBasePath()), url, name, new SvnCommitRunner.CommandListener(null),
                                      new IdeaSvnkitBasedAuthenticationCallback(vcs), false, ArrayUtil.toStringArray(parameters));
    }
    catch (SvnBindException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  public static SvnLineCommand runSimple(@NotNull SvnSimpleCommand command, SvnVcs vcs, @Nullable SVNURL url) throws SVNException {
    // empty command name passed, as command name is already in command.getParameters()
    return runSimple(SvnCommandName.empty, vcs, url, new ArrayList<String>(Arrays.asList(command.getParameters())));
  }
}
