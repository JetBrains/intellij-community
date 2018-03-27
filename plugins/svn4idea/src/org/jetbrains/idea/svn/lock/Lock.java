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
package org.jetbrains.idea.svn.lock;

import com.intellij.openapi.vcs.changes.LogicalLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.bind.annotation.*;
import java.util.Date;

/**
 * TODO: Probably unify with LogicalLock class
 */
public class Lock {

  private final String myOwner;
  private final String myComment;
  private final Date myCreationDate;
  @Nullable private final Date myExpirationDate;

  public Lock(@NotNull Lock.Builder builder) {
    myOwner = builder.owner;
    myComment = builder.comment;
    myCreationDate = builder.created;
    myExpirationDate = builder.expires;
  }

  public String getComment() {
    return myComment;
  }

  public Date getCreationDate() {
    return myCreationDate;
  }

  @Nullable
  public Date getExpirationDate() {
    return myExpirationDate;
  }

  public String getOwner() {
    return myOwner;
  }

  @NotNull
  public LogicalLock toLogicalLock(boolean isLocal) {
    return new LogicalLock(isLocal, myOwner, myComment, myCreationDate, myExpirationDate);
  }

  @XmlAccessorType(XmlAccessType.NONE)
  @XmlType(name = "lock")
  @XmlRootElement(name = "lock")
  public static class Builder {

    @XmlElement(name = "token")
    private String token;

    @XmlElement(name = "owner")
    private String owner;

    @XmlElement(name = "comment")
    private String comment;

    @XmlElement(name = "created")
    private Date created;

    @XmlElement(name = "expires")
    @Nullable private Date expires;

    @NotNull
    public Builder setToken(String token) {
      this.token = token;
      return this;
    }

    @NotNull
    public Builder setOwner(String owner) {
      this.owner = owner;
      return this;
    }

    @NotNull
    public Builder setComment(String comment) {
      this.comment = comment;
      return this;
    }

    @NotNull
    public Builder setCreationDate(Date creationDate) {
      this.created = creationDate;
      return this;
    }

    @NotNull
    public Builder setExpirationDate(@Nullable Date expirationDate) {
      this.expires = expirationDate;
      return this;
    }

    @NotNull
    public Lock build() {
      return new Lock(this);
    }
  }
}
