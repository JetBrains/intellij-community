// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class LogEntry {

  public static final LogEntry EMPTY = new LogEntry.Builder().setRevision(-1).setHasChildren(false).build();

  private final long myRevision;
  @Nullable private final Date myDate;
  private final String myMessage;
  private final String myAuthor;
  @NotNull private final Map<String, LogEntryPath> myChangedPaths;
  private final boolean myHasChildren;

  public LogEntry(@NotNull LogEntry.Builder builder) {
    myRevision = builder.revision;
    myChangedPaths = toImmutable(builder.changedPaths);
    myAuthor = builder.author;
    myDate = builder.date;
    myMessage = builder.message;
    myHasChildren = builder.hasChildren();
  }

  @NotNull
  private static Map<String, LogEntryPath> toImmutable(@NotNull List<LogEntryPath.Builder> paths) {
    ContainerUtil.ImmutableMapBuilder<String, LogEntryPath> builder = ContainerUtil.immutableMapBuilder();

    for (LogEntryPath.Builder path : paths) {
      builder.put(path.getPath(), path.build());
    }

    return builder.build();
  }

  @NotNull
  public Map<String, LogEntryPath> getChangedPaths() {
    return myChangedPaths;
  }

  public String getAuthor() {
    return myAuthor;
  }

  @Nullable
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

    @NotNull
    public List<LogEntry.Builder> getChildEntries() {
      return childEntries;
    }

    public boolean hasChildren() {
      return !childEntries.isEmpty();
    }

    @NotNull
    public Builder setRevision(long revision) {
      this.revision = revision;
      return this;
    }

    @NotNull
    public Builder setAuthor(String author) {
      this.author = author;
      return this;
    }

    @NotNull
    public Builder setDate(Date date) {
      this.date = date;
      return this;
    }

    @NotNull
    public Builder setMessage(String message) {
      this.message = message;
      return this;
    }

    @NotNull
    public Builder setHasChildren(boolean hasChildren) {
      // probably LogEntry interface will be changed and child entries will be specified explicitly later, but for now just use such "fake"
      // implementation for setting "hasChildren" value
      childEntries.clear();
      if (hasChildren) {
        childEntries.add(this);
      }
      return this;
    }

    @NotNull
    public Builder addPath(@NotNull LogEntryPath.Builder path) {
      changedPaths.add(path);
      return this;
    }

    @NotNull
    public LogEntry build() {
      return new LogEntry(this);
    }
  }
}
