// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private final @Nullable Date myExpirationDate;

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

  public @Nullable Date getExpirationDate() {
    return myExpirationDate;
  }

  public String getOwner() {
    return myOwner;
  }

  public @NotNull LogicalLock toLogicalLock(boolean isLocal) {
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

    @XmlElement(name = "expires") private @Nullable Date expires;

    public @NotNull Builder setToken(String token) {
      this.token = token;
      return this;
    }

    public @NotNull Builder setOwner(String owner) {
      this.owner = owner;
      return this;
    }

    public @NotNull Builder setComment(String comment) {
      this.comment = comment;
      return this;
    }

    public @NotNull Builder setCreationDate(Date creationDate) {
      this.created = creationDate;
      return this;
    }

    public @NotNull Builder setExpirationDate(@Nullable Date expirationDate) {
      this.expires = expirationDate;
      return this;
    }

    public @NotNull Lock build() {
      return new Lock(this);
    }
  }
}
