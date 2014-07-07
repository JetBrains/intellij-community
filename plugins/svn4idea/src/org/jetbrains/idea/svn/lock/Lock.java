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

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNLock;

import java.util.Date;

/**
 * TODO: Probably unify with LogicalLock class
 *
 * @author Konstantin Kolosovsky.
 */
public class Lock {

  private final String myOwner;
  private final String myComment;
  private final Date myCreationDate;
  private final Date myExpirationDate;

  @Nullable
  public static Lock create(@Nullable SVNLock lock) {
    Lock result = null;

    if (lock != null) {
      result = new Lock(lock.getOwner(), lock.getComment(), lock.getCreationDate(), lock.getExpirationDate());
    }

    return result;
  }

  public Lock(String owner, String comment, Date creationDate, Date expirationDate) {
    myOwner = owner;
    myComment = comment;
    myCreationDate = creationDate;
    myExpirationDate = expirationDate;
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

  public String getOwner() {
    return myOwner;
  }
}
