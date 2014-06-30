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

import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.Date;

/**
 * @author Konstantin Kolosovsky.
 */
public class CommitInfo {

  public static final CommitInfo EMPTY = new CommitInfo(-1, null, null, null);

  private final long myRevision;
  private final Date myDate;
  private final String myAuthor;
  @Nullable private final SVNErrorMessage myErrorMessage;

  public CommitInfo(long revision, String author, Date date) {
    this(revision, author, date, null);
  }

  public CommitInfo(long revision, String author, Date date, @Nullable SVNErrorMessage error) {
    myRevision = revision;
    myAuthor = author;
    myDate = date;
    myErrorMessage = error;
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

  @Nullable
  public SVNErrorMessage getErrorMessage() {
    return myErrorMessage;
  }
}
