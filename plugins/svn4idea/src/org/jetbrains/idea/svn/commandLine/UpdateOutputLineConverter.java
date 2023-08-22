// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.api.EventAction;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateOutputLineConverter {
  private final static @NonNls String MERGING = "--- Merging";
  private final static @NonNls String RECORDING_MERGE_INFO = "--- Recording mergeinfo";

  private final static @NonNls String UPDATING = "Updating";
  private final static @NonNls String SKIPPED = "Skipped";
  private final static @NonNls String RESTORED = "Restored";

  private final static @NonNls String FETCHING_EXTERNAL = "Fetching external";

  private final static Pattern ourAtRevision = Pattern.compile("At revision (\\d+)\\.");
  private final static Pattern ourUpdatedToRevision = Pattern.compile("Updated to revision (\\d+)\\.");
  private final static Pattern ourCheckedOutRevision = Pattern.compile("Checked out revision (\\d+)\\.");

  // export from repository
  private final static Pattern ourExportedRevision = Pattern.compile("Exported revision (\\d+)\\.");
  // export from working copy
  private final static Pattern ourExportComplete = Pattern.compile("Export complete\\.");

  // update operation with no changes - still we're interested in revision number
  private final static Pattern ourExternal = Pattern.compile("External at revision (\\d+)\\.");
  // update operation with some changes
  private final static Pattern ourUpdatedExternal = Pattern.compile("Updated external to revision (\\d+)\\.");
  private final static Pattern ourCheckedOutExternal = Pattern.compile("Checked out external at revision (\\d+)\\.");

  private final static Pattern[] ourCompletePatterns =
    new Pattern[]{ourAtRevision, ourUpdatedToRevision, ourCheckedOutRevision, ourExportedRevision, ourExternal, ourUpdatedExternal,
      ourCheckedOutExternal, ourExportComplete};

  private final File myBase;
  @NotNull private final Stack<File> myRootsUnderProcessing;

  public UpdateOutputLineConverter(File base) {
    myBase = base;
    myRootsUnderProcessing = new Stack<>();
  }

  @Nullable
  public ProgressEvent convert(final String line) {
    // TODO: Add direct processing of "Summary of conflicts" lines at the end of "svn update" output (if there are conflicts).
    // TODO: Now it works ok because parseNormalLine could not determine necessary statuses from that and further lines
    if (StringUtil.isEmptyOrSpaces(line)) return null;

    if (line.startsWith(MERGING) || line.startsWith(RECORDING_MERGE_INFO)) {
      return null;
    } else if (line.startsWith(UPDATING)) {
      myRootsUnderProcessing.push(parseForPath(line));
      return createEvent(myRootsUnderProcessing.peek(), EventAction.UPDATE_NONE);
    } else if (line.startsWith(RESTORED)) {
      return createEvent(parseForPath(line), EventAction.RESTORE);
    } else if (line.startsWith(SKIPPED)) {
      // called, for instance, when folder is not working copy
      return createEvent(parseForPath(line), -1, EventAction.SKIP, parseComment(line));
    } else if (line.startsWith(FETCHING_EXTERNAL)) {
      myRootsUnderProcessing.push(parseForPath(line));
      return createEvent(myRootsUnderProcessing.peek(), EventAction.UPDATE_EXTERNAL);
    }

    for (final Pattern pattern : ourCompletePatterns) {
      final long revision = matchAndGetRevision(pattern, line);
      if (revision != -1) {
        // checkout output does not have special line like "Updating '.'" on start - so stack could be empty and we should use myBase
        File currentRoot = myRootsUnderProcessing.size() > 0 ? myRootsUnderProcessing.pop() : myBase;
        return createEvent(currentRoot, revision, EventAction.UPDATE_COMPLETED, null);
      }
    }

    return parseNormalString(line);
  }

  @NotNull
  private static ProgressEvent createEvent(File file, @NotNull EventAction action) {
    return createEvent(file, -1, action, null);
  }

  @NotNull
  private static ProgressEvent createEvent(File file, long revision, @NotNull EventAction action, @Nullable String error) {
    return new ProgressEvent(file, revision, null, null, action, error, null);
  }

  private final static Set<Character> ourActions = Set.of('A', 'D', 'U', 'C', 'G', 'E', 'R');

  @Nullable
  private ProgressEvent parseNormalString(final String line) {
    if (line.length() < 5) return null;
    final char first = line.charAt(0);
    if (' ' != first && ! ourActions.contains(first)) return null;
    final StatusType contentsStatus = CommandUtil.getStatusType(first);
    final char second = line.charAt(1);
    final StatusType propertiesStatus = CommandUtil.getStatusType(second);
    final char lock = line.charAt(2); // dont know what to do with stolen lock info
    if (' ' != lock && 'B' != lock) return null;
    final char treeConflict = line.charAt(3);
    if (' ' != treeConflict && 'C' != treeConflict) return null;
    final boolean haveTreeConflict = 'C' == treeConflict;

    final String path = line.substring(4).trim();
    if (StringUtil.isEmptyOrSpaces(path)) return null;
    final File file = SvnUtil.resolvePath(myBase, path);
    if (StatusType.STATUS_OBSTRUCTED.equals(contentsStatus)) {
      // obstructed
      return new ProgressEvent(file, -1, contentsStatus, propertiesStatus, EventAction.UPDATE_SKIP_OBSTRUCTION, null, null);
    }

    EventAction action;
    EventAction expectedAction;
    if (StatusType.STATUS_ADDED.equals(contentsStatus)) {
      expectedAction = EventAction.UPDATE_ADD;
    } else if (StatusType.STATUS_DELETED.equals(contentsStatus)) {
      expectedAction = EventAction.UPDATE_DELETE;
    } else {
      expectedAction = EventAction.UPDATE_UPDATE;
    }
    action = expectedAction;
    if (haveTreeConflict) {
      action = EventAction.TREE_CONFLICT;
    }

    return new ProgressEvent(file, -1, contentsStatus, propertiesStatus, action, null, null);
  }

  private static long matchAndGetRevision(final Pattern pattern, final String line) {
    final Matcher matcher = pattern.matcher(line);
    if (matcher.matches()) {
      if (pattern == ourExportComplete) {
        return 0;
      }

      final String group = matcher.group(1);
      if (group == null) return -1;
      try {
        return Long.parseLong(group);
      } catch (NumberFormatException e) {
        //
      }
    }
    return -1;
  }

  @Nullable
  private static String parseComment(final String line) {
    int index = line.lastIndexOf("--");

    return index != -1 && index < line.length() - 2 ? line.substring(index + 2).trim() : null;
  }

  @Nullable
  private File parseForPath(@NotNull String line) {
    File result = null;
    int start = line.indexOf('\'');

    if (start != -1) {
      int end = line.indexOf('\'', start + 1);

      if (end != -1) {
        String path = line.substring(start + 1, end);
        result = SvnUtil.resolvePath(myBase, path);
      }
    }

    return result;
  }
}
