/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.diff.DiffOptions;
import org.jetbrains.idea.svn.status.StatusType;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.StringReader;
import java.util.List;

public class CommandUtil {

  private static final Logger LOG = Logger.getInstance(CommandUtil.class);

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
    put(parameters, path.getAbsolutePath(), Revision.UNDEFINED);
  }

  public static void put(@NotNull List<String> parameters, @NotNull File path, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, path);
    } else {
      parameters.add(path.getAbsolutePath());
    }
  }

  public static void put(@NotNull List<String> parameters, @NotNull File path, @Nullable Revision pegRevision) {
    put(parameters, path.getAbsolutePath(), pegRevision);
  }

  public static void put(@NotNull List<String> parameters, @NotNull String path, @Nullable Revision pegRevision) {
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

  public static void put(@NotNull List<String> parameters, @NotNull Target target) {
    put(parameters, target.getPath(), target.getPegRevision());
  }

  public static void put(@NotNull List<String> parameters, @NotNull Target target, boolean usePegRevision) {
    if (usePegRevision) {
      put(parameters, target);
    } else {
      parameters.add(target.getPath());
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable Depth depth) {
    put(parameters, depth, false);
  }

  public static void put(@NotNull List<String> parameters, @Nullable Depth depth, boolean sticky) {
    if (depth != null && !Depth.UNKNOWN.equals(depth)) {
      parameters.add("--depth");
      parameters.add(depth.getName());

      if (sticky) {
        parameters.add("--set-depth");
        parameters.add(depth.getName());
      }
    }
  }

  public static void put(@NotNull List<String> parameters, @Nullable Revision revision) {
    if (revision != null && !Revision.UNDEFINED.equals(revision) && !Revision.WORKING.equals(revision) && revision.isValid()) {
      parameters.add("--revision");
      parameters.add(format(revision));
    }
  }

  public static void put(@NotNull List<String> parameters, @NotNull Revision startRevision, @NotNull Revision endRevision) {
    parameters.add("--revision");
    parameters.add(format(startRevision) + ":" + format(endRevision));
  }

  @NotNull
  public static String format(@NotNull Revision revision) {
    return revision.getDate() != null ? "{" + DateFormatUtil.getIso8601Format().format(revision.getDate()) + "}" : revision.toString();
  }

  public static void put(@NotNull List<String> parameters, @Nullable DiffOptions diffOptions) {
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

    return (T) unmarshaller.unmarshal(new StringReader(data.trim()));
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

    return ObjectUtils.assertNotNull(result);
  }
}
