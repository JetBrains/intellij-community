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

  @NotNull
  public NodeKind getKind() {
    return myKind;
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

    @NotNull
    public Builder setKind(@NotNull NodeKind kind) {
      this.kind = kind;
      return this;
    }

    @NotNull
    public Builder setType(char type) {
      this.action = String.valueOf(type);
      return this;
    }

    @NotNull
    public Builder setCopyFromPath(String copyFromPath) {
      this.copyFromPath = copyFromPath;
      return this;
    }

    @NotNull
    public Builder setCopyFromRevision(long copyFromRevision) {
      this.copyFromRevision = copyFromRevision;
      return this;
    }

    @NotNull
    public Builder setPath(String path) {
      this.path = path;
      return this;
    }

    @NotNull
    public LogEntryPath build() {
      return new LogEntryPath(this);
    }
  }
}
