package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnApplicationSettings;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.FileStatusResultParser;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.StringReader;
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
    String exe = resolveExePath();
    base = resolveBaseDirectory(base, exe);
    url = resolveRepositoryUrl(vcs, url);

    try {
      return SvnLineCommand
        .runWithAuthenticationAttempt(exe, base, url, name, new SvnCommitRunner.CommandListener(null),
                                      new IdeaSvnkitBasedAuthenticationCallback(vcs), ArrayUtil.toStringArray(parameters));
    }
    catch (SvnBindException e) {
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e), e);
    }
  }

  @Nullable
  private static SVNURL resolveRepositoryUrl(@NotNull SvnVcs vcs, @Nullable SVNURL url) {
    if (url == null) {
      // TODO: or take it from RootUrlInfo
      SVNInfo info = vcs.getInfo(vcs.getProject().getBaseDir());

      url = info != null ? info.getURL() : null;
    }
    return url;
  }

  @NotNull
  private static File resolveBaseDirectory(@Nullable File base, @NotNull String defaultBase) {
    return base == null ? new File(defaultBase) : base;
  }

  @NotNull
  private static String resolveExePath() {
    return SvnApplicationSettings.getInstance().getCommandLinePath();
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

  public static void put(@NotNull List<String> parameters, @NotNull File path, @Nullable SVNRevision pegRevision) {
    put(parameters, path.getAbsolutePath(), pegRevision);
  }

  public static void put(@NotNull List<String> parameters, @NotNull String path, @Nullable SVNRevision pegRevision) {
    StringBuilder builder = new StringBuilder(path);

    if (pegRevision != null && !SVNRevision.UNDEFINED.equals(pegRevision) && !SVNRevision.WORKING.equals(pegRevision) &&
        pegRevision.isValid() && pegRevision.getNumber() != 0) {
      builder.append("@");
      builder.append(pegRevision);
    }

    parameters.add(builder.toString());
  }

  public static void put(@NotNull List<String> parameters, @NotNull SvnTarget target) {
    put(parameters, target.getPathOrUrlString(), target.getPegRevision());
  }

  public static void put(@NotNull List<String> parameters, @NotNull File... paths) {
    for (File path : paths) {
      put(parameters, path);
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNDepth depth) {
    if (depth != null && !SVNDepth.UNKNOWN.equals(depth)) {
      parameters.add("--depth");
      parameters.add(depth.getName());
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNRevision revision) {
    if (revision != null && !SVNRevision.UNDEFINED.equals(revision) && !SVNRevision.WORKING.equals(revision) && revision.isValid()) {
      parameters.add("--revision");
      parameters.add(revision.toString());
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNDiffOptions diffOptions) {
    if (diffOptions != null) {
      StringBuilder builder = new StringBuilder();

      if (diffOptions.isIgnoreAllWhitespace()) {
        builder.append(" --ignore-space-change");
      }
      if (diffOptions.isIgnoreAmountOfWhitespace()) {
        builder.append(" --ignore-all-space");
      }
      if (diffOptions.isIgnoreEOLStyle()) {
        builder.append(" --ignore-eol-style");
      }

      String value = builder.toString().trim();

      if (!StringUtil.isEmpty(value)) {
        parameters.add("--extensions");
        parameters.add(value);
      }
    }
  }

  public static <T> T parse(@NotNull String data, @NotNull Class<T> type) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(type);
    Unmarshaller unmarshaller = context.createUnmarshaller();

    return (T) unmarshaller.unmarshal(new StringReader(data));
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
  public static SvnCommand execute(@NotNull SvnVcs vcs,
                                   @NotNull SvnCommandName name,
                                   @NotNull List<String> parameters,
                                   @Nullable FileStatusResultParser parser,
                                   @Nullable LineCommandListener listener)
  throws VcsException {
    String exe = resolveExePath();
    File base = resolveBaseDirectory(null, exe);
    SVNURL url = resolveRepositoryUrl(vcs, null);

    SvnLineCommand command = SvnLineCommand.runWithAuthenticationAttempt(
      exe, base, url, name, listener != null ? listener : new SvnCommitRunner.CommandListener(null),
      new IdeaSvnkitBasedAuthenticationCallback(vcs),
      ArrayUtil.toStringArray(parameters));

    if (parser != null) {
      parser.parse(command.getOutput());
    }

    return command;
  }

  public static SvnCommand execute(@NotNull SvnVcs vcs,
                                   @NotNull SvnCommandName name,
                                   @NotNull List<String> parameters,
                                   @Nullable FileStatusResultParser parser) throws VcsException {
    return execute(vcs, name, parameters, parser, null);
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
