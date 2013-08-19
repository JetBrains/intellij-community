package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnCommitRunner;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.FileStatusResultParser;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.jetbrains.idea.svn.config.SvnBindException;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandUtil {
  public static SvnLineCommand runSimple(@NotNull SvnCommandName name,
                                         @NotNull SvnVcs vcs,
                                         @Nullable File base,
                                         @Nullable SVNURL url,
                                         List<String> parameters)
    throws SVNException {
    String exe = SvnApplicationSettings.getInstance().getCommandLinePath();
    base = base == null ? new File(exe) : base;

    if (url == null) {
      // TODO: or take it from RootUrlInfo
      SVNInfo info = vcs.getInfo(vcs.getProject().getBaseDir());

      url = info != null ? info.getURL() : null;
    }

    try {
      return SvnLineCommand
        .runWithAuthenticationAttempt(exe, base, url, name, new SvnCommitRunner.CommandListener(null),
                                      new IdeaSvnkitBasedAuthenticationCallback(vcs), ArrayUtil.toStringArray(parameters));
    }
    catch (SvnBindException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
    }
  }

  public static SvnLineCommand runSimple(@NotNull SvnSimpleCommand command, @NotNull SvnVcs vcs, @Nullable File base, @Nullable SVNURL url)
    throws SVNException {
    // empty command name passed, as command name is already in command.getParameters()
    return runSimple(SvnCommandName.empty, vcs, base, url, new ArrayList<String>(Arrays.asList(command.getParameters())));
  }

  /**
   * Puts given value to parameters if condition is satisfied
   *
   * @param parameters
   * @param condition
   * @param value
   */
  public static void put(@NotNull List<String> parameters, boolean condition, @NotNull String value) {
    if (condition) {
      parameters.add(value);
    }
  }

  public static void put(@NotNull List<String> parameters, @NotNull File path) {
    parameters.add(path.getAbsolutePath());
  }

  public static void put(@NotNull List<String> parameters, @NotNull File... paths) {
    for (File path : paths) {
      put(parameters, path);
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNDepth depth) {
    if (depth != null) {
      parameters.add("--depth");
      parameters.add(depth.getName());
    }
  }


  /**
   * Utility method for running commands changing certain file status information.
   * // TODO: Should be replaced with non-static analogue.
   *
   * @param vcs
   * @param name
   * @param parameters
   * @param parser
   * @throws VcsException
   */
  public static void execute(@NotNull SvnVcs vcs,
                             @NotNull SvnCommandName name,
                             @NotNull List<String> parameters,
                             @Nullable FileStatusResultParser parser)
  throws VcsException {
    try {
      SvnLineCommand command = runSimple(name, vcs, null, null, parameters);

      if (parser != null) {
        parser.parse(command.getOutput());
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
  }


  /**
   * Gets svn status represented by single character.
   *
   * @param type
   * @return
   */
  public static char getStatusChar(@Nullable String type) {
    return !StringUtil.isEmpty(type) ? type.charAt(0) : ' ';
  }

  @NotNull
  public static SVNStatusType getStatusType(@Nullable String type) {
    return getStatusType(getStatusChar(type));
  }

  @NotNull
  public static SVNStatusType getStatusType(char first) {
    final SVNStatusType contentsStatus;
    if ('A' == first) {
      contentsStatus = SVNStatusType.STATUS_ADDED;
    } else if ('D' == first) {
      contentsStatus = SVNStatusType.STATUS_DELETED;
    } else if ('U' == first) {
      contentsStatus = SVNStatusType.CHANGED;
    } else if ('C' == first) {
      contentsStatus = SVNStatusType.CONFLICTED;
    } else if ('G' == first) {
      contentsStatus = SVNStatusType.MERGED;
    } else if ('R' == first) {
      contentsStatus = SVNStatusType.STATUS_REPLACED;
    } else if ('E' == first) {
      contentsStatus = SVNStatusType.STATUS_OBSTRUCTED;
    } else {
      contentsStatus = SVNStatusType.STATUS_NORMAL;
    }
    return contentsStatus;
  }
}
