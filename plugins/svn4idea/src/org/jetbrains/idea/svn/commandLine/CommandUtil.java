package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
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
