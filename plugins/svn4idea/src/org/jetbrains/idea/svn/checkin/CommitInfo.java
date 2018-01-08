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
package org.jetbrains.idea.svn.checkin;

import org.jetbrains.annotations.NotNull;

import javax.xml.bind.annotation.*;
import java.util.Date;

public class CommitInfo {

  public static final CommitInfo EMPTY = new CommitInfo.Builder().setRevision(-1).build();

  private final long myRevision;
  private final Date myDate;
  private final String myAuthor;

  private CommitInfo(@NotNull CommitInfo.Builder builder) {
    myRevision = builder.revision;
    myAuthor = builder.author;
    myDate = builder.date;
  }

  public long getRevision() {
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

    @XmlAttribute(name = "revision")
    private long revision;

    @XmlElement(name = "author")
    private String author;

    @XmlElement(name = "date")
    private Date date;

    public Builder() {
    }

    public Builder(long revision, Date date, String author) {
      this.revision = revision;
      this.date = date;
      this.author = author;
    }

    public long getRevision() {
      return revision;
    }

    public String getAuthor() {
      return author;
    }

    public Date getDate() {
      return date;
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
    public CommitInfo build() {
      return new CommitInfo(this);
    }
  }
}
