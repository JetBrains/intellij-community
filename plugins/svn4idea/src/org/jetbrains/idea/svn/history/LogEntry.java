/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.io.SVNRepository;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * @author Konstantin Kolosovsky.
 */
public class LogEntry {

  public static final LogEntry EMPTY =
    new LogEntry(Collections.<String, SVNLogEntryPath>emptyMap(), SVNRepository.INVALID_REVISION, null, null, null, false);

  private final long myRevision;
  private final Date myDate;
  private final String myMessage;
  private final String myAuthor;
  @NotNull private final Map<String, SVNLogEntryPath> myChangedPaths;
  private boolean myHasChildren;

  @Nullable
  public static LogEntry create(@Nullable SVNLogEntry entry) {
    LogEntry result = null;

    if (entry != null) {
      result = new LogEntry(entry.getChangedPaths(), entry.getRevision(), entry.getAuthor(), entry.getDate(), entry.getMessage(),
                            entry.hasChildren());
    }

    return result;
  }

  public LogEntry(@Nullable Map<String, SVNLogEntryPath> changedPaths,
                  long revision,
                  String author,
                  Date date,
                  String message,
                  boolean hasChildren) {
    myRevision = revision;
    myChangedPaths = toImmutable(changedPaths);
    myAuthor = author;
    myDate = date;
    myMessage = message;
    myHasChildren = hasChildren;
  }

  @NotNull
  private static Map<String, SVNLogEntryPath> toImmutable(@Nullable Map<String, SVNLogEntryPath> paths) {
    ContainerUtil.ImmutableMapBuilder<String, SVNLogEntryPath> builder = ContainerUtil.immutableMapBuilder();

    if (paths != null) {
      for (Map.Entry<String, SVNLogEntryPath> entry : paths.entrySet()) {
        builder.put(entry.getKey(), entry.getValue());
      }
    }

    return builder.build();
  }

  @NotNull
  public Map<String, SVNLogEntryPath> getChangedPaths() {
    return myChangedPaths;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public Date getDate() {
    return myDate;
  }

  public String getMessage() {
    return myMessage;
  }

  public long getRevision() {
    return myRevision;
  }

  public boolean hasChildren() {
    return myHasChildren;
  }
}
