// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.checkin;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Revision;

import javax.xml.bind.annotation.*;
import java.util.Date;

public final class CommitInfo {

  public static final CommitInfo EMPTY = new CommitInfo.Builder().setRevisionNumber(-1).build();

  private final long myRevisionNumber;
  private final @NotNull Revision myRevision;
  private final Date myDate;
  private final String myAuthor;

  private CommitInfo(@NotNull CommitInfo.Builder builder) {
    myRevisionNumber = builder.revisionNumber;
    myRevision = Revision.of(myRevisionNumber);
    myAuthor = builder.author;
    myDate = builder.date;
  }

  public long getRevisionNumber() {
    return myRevisionNumber;
  }

  public @NotNull Revision getRevision() {
    return myRevision;
  }

  public @NlsSafe String getAuthor() {
    return myAuthor;
  }

  public Date getDate() {
    return myDate;
  }

  @XmlAccessorType(XmlAccessType.NONE)
  @XmlType(name = "commit")
  @XmlRootElement(name = "commit")
  public static class Builder {

    @XmlAttribute(name = "revision", required = true)
    private long revisionNumber;

    @XmlElement(name = "author")
    private String author;

    @XmlElement(name = "date")
    private Date date;

    public Builder() {
    }

    public Builder(long revisionNumber, Date date, String author) {
      this.revisionNumber = revisionNumber;
      this.date = date;
      this.author = author;
    }

    public long getRevisionNumber() {
      return revisionNumber;
    }

    public String getAuthor() {
      return author;
    }

    public Date getDate() {
      return date;
    }

    public @NotNull Builder setRevisionNumber(long revisionNumber) {
      this.revisionNumber = revisionNumber;
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

    public @NotNull CommitInfo build() {
      return new CommitInfo(this);
    }
  }
}
