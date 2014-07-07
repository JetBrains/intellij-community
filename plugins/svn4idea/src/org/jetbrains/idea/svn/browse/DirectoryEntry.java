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
package org.jetbrains.idea.svn.browse;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;

import java.util.Date;

/**
 * @author Konstantin Kolosovsky.
 */
public class DirectoryEntry implements Comparable<DirectoryEntry> {

  private final String myName;
  private final SVNNodeKind myKind;
  private final long myRevision;
  private final Date myDate;
  private final String myAuthor;
  private final String myPath;
  private final SVNURL myUrl;
  private final SVNURL myRepositoryRoot;

  @NotNull
  public static DirectoryEntry create(@NotNull SVNDirEntry entry) {
    return new DirectoryEntry(entry.getURL(), entry.getRepositoryRoot(), entry.getName(), entry.getKind(), entry.getRevision(), entry.getDate(),
                     entry.getAuthor(), entry.getRelativePath());
  }

  public DirectoryEntry(SVNURL url,
                        SVNURL repositoryRoot,
                        String name,
                        SVNNodeKind kind,
                        long revision,
                        Date date,
                        String author,
                        String path) {
    myUrl = url;
    myRepositoryRoot = repositoryRoot;
    myName = name;
    myKind = kind;
    myRevision = revision;
    myDate = date;
    myAuthor = author;
    myPath = path;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public SVNURL getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public String getName() {
    return myName;
  }

  public SVNNodeKind getKind() {
    return myKind;
  }

  public Date getDate() {
    return myDate;
  }

  public long getRevision() {
    return myRevision;
  }

  public String getAuthor() {
    return myAuthor;
  }

  public String getRelativePath() {
    return myPath == null ? myName : myPath;
  }

  @Override
  public int compareTo(@NotNull DirectoryEntry o) {
    int result = getKind().compareTo(o.getKind());

    return result != 0 ? result : myUrl.toString().compareTo(o.getUrl().toString());
  }
}
