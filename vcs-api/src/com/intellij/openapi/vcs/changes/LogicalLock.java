package com.intellij.openapi.vcs.changes;

import java.util.Date;

public class LogicalLock {
  private final boolean myIsLocal;
  private final String myOwner;
  private final String myComment;
  private final Date myCreationDate;
  private final Date myExpirationDate;

  public LogicalLock(boolean isLocal, String owner, String comment, Date creationDate, Date expirationDate) {
    myIsLocal = isLocal;
    myOwner = owner;
    myComment = comment;
    myCreationDate = creationDate;
    myExpirationDate = expirationDate;
  }

  public String getOwner() {
    return myOwner;
  }

  public String getComment() {
    return myComment;
  }

  public Date getCreationDate() {
    return myCreationDate;
  }

  public Date getExpirationDate() {
    return myExpirationDate;
  }

  public boolean isIsLocal() {
    return myIsLocal;
  }
}
