// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.checkin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Revision;

import javax.xml.bind.annotation.*;
import java.util.Date;

public class CommitInfo {

  public static final CommitInfo EMPTY = new CommitInfo.Builder().setRevisionNumber(-1).build();

  private final long myRevisionNumber;
  @NotNull private final Revision myRevision;
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

  @NotNull
  public Revision getRevision() {
    return myRevision;
  }

  public String getAuthor() {
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

    @NotNull
    public Builder setRevisionNumber(long revisionNumber) {
      this.revisionNumber = revisionNumber;
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
    public CommitInfo build() {
      return new CommitInfo(this);
    }
  }
}
