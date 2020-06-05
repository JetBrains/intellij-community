// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.util.*;

public final class LogEntry {
  public static final LogEntry EMPTY = new LogEntry.Builder().setRevision(-1).setHasChildren(false).build();

  private final long myRevision;
  private final @Nullable Date myDate;
  private final String myMessage;
  private final String myAuthor;
  private final @NotNull Map<String, LogEntryPath> myChangedPaths;
  private final boolean myHasChildren;

  public LogEntry(@NotNull LogEntry.Builder builder) {
    myRevision = builder.revision;
    myChangedPaths = toImmutable(builder.changedPaths);
    myAuthor = builder.author;
    myDate = builder.date;
    myMessage = builder.message;
    myHasChildren = builder.hasChildren();
  }

  private static @NotNull Map<String, LogEntryPath> toImmutable(@NotNull List<LogEntryPath.Builder> paths) {
    Map<String, LogEntryPath> result = new HashMap<>();
    for (LogEntryPath.Builder path : paths) {
      result.put(path.getPath(), path.build());
    }
    return Collections.unmodifiableMap(result);
  }

  public @NotNull Map<String, LogEntryPath> getChangedPaths() {
    return myChangedPaths;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public @Nullable Date getDate() {
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

  @XmlAccessorType(XmlAccessType.NONE)
  // type explicitly specified not to conflict with LogEntryPath.Builder
  @XmlType(name = "logentry")
  public static class Builder {

    @XmlAttribute(name = "revision")
    private long revision;

    @XmlElement(name = "author")
    private String author;

    @XmlElement(name = "date")
    private Date date;

    @XmlElement(name = "msg")
    private String message;

    @XmlElementWrapper(name = "paths")
    @XmlElement(name = "path")
    private final List<LogEntryPath.Builder> changedPaths = new ArrayList<>();

    @XmlElement(name = "logentry")
    private final List<LogEntry.Builder> childEntries = new ArrayList<>();

    public @NotNull List<LogEntry.Builder> getChildEntries() {
      return childEntries;
    }

    public boolean hasChildren() {
      return !childEntries.isEmpty();
    }

    public @NotNull Builder setRevision(long revision) {
      this.revision = revision;
      return this;
    }

    public @NotNull Builder setAuthor(String author) {
      this.author = author;
      return this;
    }

    public @NotNull Builder setDate(Date date) {
      this.date = date;
      return this;
    }

    public @NotNull Builder setMessage(String message) {
      this.message = message;
      return this;
    }

    public @NotNull Builder setHasChildren(boolean hasChildren) {
      // probably LogEntry interface will be changed and child entries will be specified explicitly later, but for now just use such "fake"
      // implementation for setting "hasChildren" value
      childEntries.clear();
      if (hasChildren) {
        childEntries.add(this);
      }
      return this;
    }

    public @NotNull Builder addPath(@NotNull LogEntryPath.Builder path) {
      changedPaths.add(path);
      return this;
    }

    public @NotNull LogEntry build() {
      return new LogEntry(this);
    }
  }
}
