// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.commandLine.CommandUtil;

import javax.xml.bind.annotation.*;

public class LogEntryPath extends BaseNodeDescription {

  private final String myPath;
  private final char myType;
  private final String myCopyPath;
  private final long myCopyRevision;

  public LogEntryPath(@NotNull LogEntryPath.Builder builder) {
    super(builder.kind);
    myPath = builder.path;
    myType = CommandUtil.getStatusChar(builder.action);
    myCopyPath = builder.copyFromPath;
    myCopyRevision = builder.copyFromRevision;
  }

  public String getCopyPath() {
    return myCopyPath;
  }

  public long getCopyRevision() {
    return myCopyRevision;
  }

  public String getPath() {
    return myPath;
  }

  public char getType() {
    return myType;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  // type explicitly specified not to conflict with LogEntry.Builder
  @XmlType(name = "logentrypath")
  public static class Builder {

    // empty string could be here if repository was < 1.6 when committing (see comments in schema for svn client xml output , in
    // svn source code repository) - this will result in kind = NodeKind.UNKNOWN
    @XmlAttribute(name = "kind", required = true)
    private NodeKind kind;

    @XmlAttribute(name = "action")
    private String action;

    @XmlAttribute(name = "copyfrom-path")
    private String copyFromPath;

    @XmlAttribute(name = "copyfrom-rev")
    private long copyFromRevision;

    @XmlValue
    private String path;

    public String getPath() {
      return path;
    }

    public @NotNull Builder setKind(@NotNull NodeKind kind) {
      this.kind = kind;
      return this;
    }

    public @NotNull Builder setType(char type) {
      this.action = String.valueOf(type);
      return this;
    }

    public @NotNull Builder setCopyFromPath(String copyFromPath) {
      this.copyFromPath = copyFromPath;
      return this;
    }

    public @NotNull Builder setCopyFromRevision(long copyFromRevision) {
      this.copyFromRevision = copyFromRevision;
      return this;
    }

    public @NotNull Builder setPath(String path) {
      this.path = path;
      return this;
    }

    public @NotNull LogEntryPath build() {
      return new LogEntryPath(this);
    }
  }
}
