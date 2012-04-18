/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.commandLine;

import org.tmatesoft.svn.core.SVNLock;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/21/12
 * Time: 2:33 PM
 */
public class SVNLockWrapper {
  private String myPath;
  private String myID;
  private String myOwner;
  private String myComment;
  private Date myCreationDate;
  private Date myExpirationDate;

  public SVNLockWrapper(String path, String ID, String owner, String comment, Date creationDate, Date expirationDate) {
    myPath = path;
    myID = ID;
    myOwner = owner;
    myComment = comment;
    myCreationDate = creationDate;
    myExpirationDate = expirationDate;
  }

  public SVNLockWrapper() {
  }

  public SVNLock create() {
    return new SVNLock(myPath, myID, myOwner, myComment, myCreationDate, myExpirationDate);
  }

  public String getPath() {
    return myPath;
  }

  public void setPath(String path) {
    myPath = path;
  }

  public String getID() {
    return myID;
  }

  public void setID(String ID) {
    myID = ID;
  }

  public String getOwner() {
    return myOwner;
  }

  public void setOwner(String owner) {
    myOwner = owner;
  }

  public String getComment() {
    return myComment;
  }

  public void setComment(String comment) {
    myComment = comment;
  }

  public Date getCreationDate() {
    return myCreationDate;
  }

  public void setCreationDate(Date creationDate) {
    myCreationDate = creationDate;
  }

  public Date getExpirationDate() {
    return myExpirationDate;
  }

  public void setExpirationDate(Date expirationDate) {
    myExpirationDate = expirationDate;
  }
}
