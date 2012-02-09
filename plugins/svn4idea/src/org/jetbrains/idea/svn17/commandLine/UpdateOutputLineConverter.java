/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn17.commandLine;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/1/12
 * Time: 5:13 PM
 */
public class UpdateOutputLineConverter {
  private final static String UPDATING = "Updating";
  private final static String AT_REVISION = "At revision (\\d+)\\.";
  private final static String UPDATED_TO_REVISION = "Updated to revision (\\d+)\\.";
  private final static String SKIPPED = "Skipped";
  private final static String RESTORED = "Restored";

  private final static String FETCHING_EXTERNAL = "Fetching external";
  private final static String EXTERNAL = "External at (\\d+)\\.";
  private final static String UPDATED_EXTERNAL = "Updated external to revision (\\d+)\\.";
  
  private final static Pattern ourAtRevision = Pattern.compile(AT_REVISION);
  private final static Pattern ourUpdatedToRevision = Pattern.compile(UPDATED_TO_REVISION);

  private final static Pattern ourExternal = Pattern.compile(EXTERNAL);
  private final static Pattern ourUpdatedExternal = Pattern.compile(UPDATED_EXTERNAL);
  
  private final static Pattern[] ourCompletePatterns = new Pattern[] {ourAtRevision, ourUpdatedToRevision, ourExternal, ourUpdatedExternal};

  private final File myBase;
  private File myCurrentFile;

  public UpdateOutputLineConverter(File base) {
    myBase = base;
  }

  public SVNEvent convert(final String line) {
    if (StringUtil.isEmptyOrSpaces(line)) return null;

    if (line.startsWith(UPDATING)) {
      myCurrentFile = parseForPath(line);
      return new SVNEvent(myCurrentFile, myCurrentFile == null ? null : (myCurrentFile.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE),
                    null, -1, null, null, null, null, SVNEventAction.UPDATE_NONE, SVNEventAction.UPDATE_NONE, null, null, null, null, null);
    } else if (line.startsWith(RESTORED)) {
      myCurrentFile = parseForPath(line);
      return new SVNEvent(myCurrentFile, myCurrentFile == null ? null : (myCurrentFile.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE),
                          null, -1, null, null, null, null, SVNEventAction.RESTORE, SVNEventAction.RESTORE, null, null, null, null, null);
    } else if (line.startsWith(SKIPPED)) {
      myCurrentFile = parseForPath(line);
      final String comment = parseComment(line);
      return new SVNEvent(myCurrentFile, myCurrentFile == null ? null : (myCurrentFile.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE),
                    null, -1, null, null, null, null, SVNEventAction.SKIP, SVNEventAction.SKIP,
                    comment == null ? null : SVNErrorMessage.create(SVNErrorCode.WC_OBSTRUCTED_UPDATE, comment), null, null, null, null);
    } else if (line.startsWith(FETCHING_EXTERNAL)) {
      myCurrentFile = parseForPath(line);
      return new SVNEvent(myCurrentFile, myCurrentFile == null ? null : (myCurrentFile.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE),
                          null, -1, null, null, null, null, SVNEventAction.UPDATE_EXTERNAL, SVNEventAction.UPDATE_EXTERNAL, null, null, null, null, null);
    }

    for (int i = 0; i < ourCompletePatterns.length; i++) {
      final Pattern pattern = ourCompletePatterns[i];
      final long revision = matchAndGetRevision(pattern, line);
      if (revision != -1) {
        return new SVNEvent(myCurrentFile, myCurrentFile == null ? null : (myCurrentFile.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE),
                    null, revision, null, null, null, null, SVNEventAction.UPDATE_COMPLETED, SVNEventAction.UPDATE_COMPLETED, null, null, null, null, null);
      }
    }

    return parseNormalString(line);
  }

  private final static Set<Character> ourActions = new HashSet<Character>(Arrays.asList(new Character[] {'A', 'D', 'U', 'C', 'G', 'E', 'R'}));

  @Nullable
  private SVNEvent parseNormalString(final String line) {
    if (line.length() < 5) return null;
    final char first = line.charAt(0);
    if (' ' != first && ! ourActions.contains(first)) return null;
    final SVNStatusType contentsStatus = getStatusType(first);
    final char second = line.charAt(1);
    final SVNStatusType propertiesStatus = getStatusType(second);
    final char lock = line.charAt(2); // dont know what to do with stolen lock info
    if (' ' != lock && 'B' != lock) return null;
    final char treeConflict = line.charAt(3);
    if (' ' != treeConflict && 'C' != treeConflict) return null;
    final boolean haveTreeConflict = 'C' == treeConflict;

    final String path = line.substring(4).trim();
    if (StringUtil.isEmptyOrSpaces(path)) return null;
    final File file = new File(myBase, path);
    if (SVNStatusType.STATUS_OBSTRUCTED.equals(contentsStatus)) {
      // obstructed
      return new SVNEvent(file, file.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE,
                    null, -1, contentsStatus, propertiesStatus, null, null, SVNEventAction.UPDATE_SKIP_OBSTRUCTION, SVNEventAction.UPDATE_ADD,
                    null, null, null, null, null);
    }
    
    SVNEventAction action;
    SVNEventAction expectedAction;
    if (SVNStatusType.STATUS_ADDED.equals(contentsStatus)) {
      expectedAction = SVNEventAction.UPDATE_ADD;
    } else if (SVNStatusType.STATUS_DELETED.equals(contentsStatus)) {
      expectedAction = SVNEventAction.UPDATE_DELETE;
    } else {
      expectedAction = SVNEventAction.UPDATE_UPDATE;
    }
    action = expectedAction;
    if (haveTreeConflict) {
      action = SVNEventAction.TREE_CONFLICT;
    }

    return new SVNEvent(file, file.isDirectory() ? SVNNodeKind.DIR : SVNNodeKind.FILE, null, -1, contentsStatus, propertiesStatus, null,
                        null, action, expectedAction, null, null, null, null, null);
  }

  private SVNStatusType getStatusType(char first) {
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

  @Nullable
  private long matchAndGetRevision(final Pattern pattern, final String line) {
    final Matcher matcher = pattern.matcher(line);
    if (matcher.matches()) {
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
  private String parseComment(final String line) {
    final int idx = line.lastIndexOf("--");
    if (idx != -1 && idx < (line.length() - 2)) {
      return line.substring(idx + 2).trim();
    }
    return null;
  }

  @Nullable
  private File parseForPath(final String line) {
    final int idx1 = line.indexOf('\'');
    if (idx1 == -1) return null;
    final int idx2 = line.indexOf('\'', idx1 + 1);
    if (idx2 == -1) return null;
    final String substring = line.substring(idx1 + 1, idx2);
    if (".".equals(substring)) return myBase;
    return new File(myBase, substring);
  }
}
