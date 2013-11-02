package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.checkin.IdeaSvnkitBasedAuthenticationCallback;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.StringReader;
import java.util.List;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommandUtil {

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
    put(parameters, path.getAbsolutePath(), SVNRevision.UNDEFINED);
  }

  public static void put(@NotNull List<String> parameters, @NotNull File path, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, path);
    } else {
      parameters.add(path.getAbsolutePath());
    }
  }

  public static void put(@NotNull List<String> parameters, @NotNull File path, @Nullable SVNRevision pegRevision) {
    put(parameters, path.getAbsolutePath(), pegRevision);
  }

  public static void put(@NotNull List<String> parameters, @NotNull String path, @Nullable SVNRevision pegRevision) {
    StringBuilder builder = new StringBuilder(path);

    boolean hasAtSymbol = path.contains("@");
    boolean hasPegRevision = pegRevision != null &&
                             !SVNRevision.UNDEFINED.equals(pegRevision) &&
                             !SVNRevision.WORKING.equals(pegRevision) &&
                             pegRevision.isValid() &&
                             pegRevision.getNumber() != 0;

    if (hasPegRevision || hasAtSymbol) {
      // add '@' to correctly handle paths that contain '@' symbol
      builder.append("@");
    }
    if (hasPegRevision) {
      builder.append(pegRevision);
    }

    parameters.add(builder.toString());
  }

  public static void put(@NotNull List<String> parameters, @NotNull SvnTarget target) {
    put(parameters, target.getPathOrUrlString(), target.getPegRevision());
  }

  public static void put(@NotNull List<String> parameters, @NotNull SvnTarget target, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, target);
    } else {
      parameters.add(target.getPathOrUrlString());
    }
  }

  public static void put(@NotNull List<String> parameters, @NotNull File... paths) {
    for (File path : paths) {
      put(parameters, path);
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNDepth depth) {
    put(parameters, depth, false);
  }

  public static void put(@NotNull List<String> parameters, @Nullable SVNDepth depth, boolean sticky) {
    if (depth != null && !SVNDepth.UNKNOWN.equals(depth)) {
      parameters.add("--depth");
      parameters.add(depth.getName());

      if (sticky) {
        parameters.add("--set-depth");
        parameters.add(depth.getName());
      }
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

  public static void putChangeLists(@NotNull List<String> parameters, @Nullable Iterable<String> changeLists) {
    if (changeLists != null) {
      for (String changeList : changeLists) {
        parameters.add("--cl");
        parameters.add(changeList);
      }
    }
  }

  public static String escape(@NotNull String path) {
    return path.contains("@") ? path + "@" : path;
  }

  public static <T> T parse(@NotNull String data, @NotNull Class<T> type) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(type);
    Unmarshaller unmarshaller = context.createUnmarshaller();

    return (T) unmarshaller.unmarshal(new StringReader(data));
  }

  /**
   * Utility method for running commands.
   * // TODO: Should be replaced with non-static analogue.
   *
   * @param vcs
   * @param target
   * @param name
   * @param parameters
   * @param listener
   * @throws VcsException
   */
  public static CommandExecutor execute(@NotNull SvnVcs vcs,
                                   @NotNull SvnTarget target,
                                   @NotNull SvnCommandName name,
                                   @NotNull List<String> parameters,
                                   @Nullable LineCommandListener listener) throws VcsException {
    return execute(vcs, target, null, name, parameters, listener);
  }

  public static CommandExecutor execute(@NotNull SvnVcs vcs,
                                   @NotNull SvnTarget target,
                                   @Nullable File workingDirectory,
                                   @NotNull SvnCommandName name,
                                   @NotNull List<String> parameters,
                                   @Nullable LineCommandListener listener) throws VcsException {
    Command command = new Command(name);

    command.setTarget(target);
    command.setWorkingDirectory(workingDirectory);
    command.setResultBuilder(listener);
    command.put(parameters);

    CommandRuntime runtime = new CommandRuntime(vcs, new IdeaSvnkitBasedAuthenticationCallback(vcs));
    return runtime.runWithAuthenticationAttempt(command);
  }

  @NotNull
  public static File getHomeDirectory() {
    return new File(PathManager.getHomePath());
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

  public static File correctUpToExistingParent(File base) {
    while (base != null) {
      if (base.exists() && base.isDirectory()) return base;
      base = base.getParentFile();
    }
    return null;
  }
}
