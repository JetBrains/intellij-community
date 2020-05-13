// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.status.StatusType;

import javax.xml.bind.*;
import java.io.File;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class CommandUtil {

  private static final Logger LOG = Logger.getInstance(CommandUtil.class);

  private static final Map<Class<?>, JAXBContext> cachedContexts = new ConcurrentHashMap<>();

  /**
   * Puts given value to parameters if condition is satisfied
   *
   * @param parameters
   * @param condition
   * @param value
   */
  public static void put(@NotNull List<? super String> parameters, boolean condition, @NotNull String value) {
    if (condition) {
      parameters.add(value);
    }
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull File path) {
    put(parameters, path.getAbsolutePath(), Revision.UNDEFINED);
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull File path, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, path);
    } else {
      parameters.add(path.getAbsolutePath());
    }
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull File path, @Nullable Revision pegRevision) {
    put(parameters, path.getAbsolutePath(), pegRevision);
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull String path, @Nullable Revision pegRevision) {
    parameters.add(format(path, pegRevision));
  }

  @NotNull
  public static String format(@NotNull String path, @Nullable Revision pegRevision) {
    StringBuilder builder = new StringBuilder(path);

    boolean hasAtSymbol = path.contains("@");
    boolean hasPegRevision = pegRevision != null &&
                             !Revision.UNDEFINED.equals(pegRevision) &&
                             !Revision.WORKING.equals(pegRevision) &&
                             pegRevision.isValid();

    if (hasPegRevision || hasAtSymbol) {
      // add '@' to correctly handle paths that contain '@' symbol
      builder.append("@");
    }
    if (hasPegRevision) {
      builder.append(format(pegRevision));
    }

    return builder.toString();
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull Target target) {
    put(parameters, target.getPath(), target.getPegRevision());
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull Target target, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, target);
    } else {
      parameters.add(target.getPath());
    }
  }

  public static void put(@NotNull List<? super String> parameters, @Nullable Depth depth) {
    put(parameters, depth, false);
  }

  public static void put(@NotNull List<? super String> parameters, @Nullable Depth depth, boolean sticky) {
    if (depth != null && !Depth.UNKNOWN.equals(depth)) {
      parameters.add("--depth");
      parameters.add(depth.getName());

      if (sticky) {
        parameters.add("--set-depth");
        parameters.add(depth.getName());
      }
    }
  }

  public static void put(@NotNull List<? super String> parameters, @Nullable Revision revision) {
    if (revision != null && !Revision.UNDEFINED.equals(revision) && !Revision.WORKING.equals(revision) && revision.isValid()) {
      parameters.add("--revision");
      parameters.add(format(revision));
    }
  }

  public static void put(@NotNull List<? super String> parameters, @NotNull Revision startRevision, @NotNull Revision endRevision) {
    parameters.add("--revision");
    parameters.add(format(startRevision) + ":" + format(endRevision));
  }

  @NotNull
  public static String format(@NotNull Revision revision) {
    return revision.getDate() != null ? "{" + DateFormatUtil.getIso8601Format().format(revision.getDate()) + "}" : revision.toString();
  }

  public static void put(@NotNull List<? super String> parameters, @Nullable DiffOptions diffOptions) {
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

  public static void putChangeLists(@NotNull List<? super String> parameters, @Nullable Iterable<String> changeLists) {
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
    if (!cachedContexts.containsKey(type)) {
      cachedContexts.put(type, JAXBContext.newInstance(type));
    }
    JAXBContext context = cachedContexts.get(type);
    Unmarshaller unmarshaller = context.createUnmarshaller();

    unmarshaller.setEventHandler(new ValidationEventHandler() {
      @Override
      public boolean handleEvent(@NotNull ValidationEvent event) {
        // Fail on exceptions, but not on other errors like "unexpected element" as sometimes we do not use all provided xml data.
        return event.getLinkedException() == null;
      }
    });
    //noinspection unchecked
    return (T)unmarshaller.unmarshal(new StringReader(data.trim()));
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
  public static StatusType getStatusType(@Nullable String type) {
    return getStatusType(getStatusChar(type));
  }

  @NotNull
  public static StatusType getStatusType(char first) {
    final StatusType contentsStatus;
    if ('A' == first) {
      contentsStatus = StatusType.STATUS_ADDED;
    } else if ('D' == first) {
      contentsStatus = StatusType.STATUS_DELETED;
    } else if ('U' == first) {
      contentsStatus = StatusType.CHANGED;
    } else if ('C' == first) {
      contentsStatus = StatusType.CONFLICTED;
    } else if ('G' == first) {
      contentsStatus = StatusType.MERGED;
    } else if ('R' == first) {
      contentsStatus = StatusType.STATUS_REPLACED;
    } else if ('E' == first) {
      contentsStatus = StatusType.STATUS_OBSTRUCTED;
    } else {
      contentsStatus = StatusType.STATUS_NORMAL;
    }
    return contentsStatus;
  }

  @Nullable
  public static File findExistingParent(@Nullable File file) {
    while (file != null) {
      if (file.exists() && file.isDirectory()) return file;
      file = file.getParentFile();
    }
    return null;
  }

  @NotNull
  public static File requireExistingParent(@NotNull File file) {
    File result = findExistingParent(file);

    if (result == null) {
      LOG.error("Existing parent not found for " + file.getAbsolutePath());
    }

    return Objects.requireNonNull(result);
  }
}
