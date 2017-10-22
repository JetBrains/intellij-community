// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.browse;

import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.checkin.CommitInfo;

import java.util.Comparator;
import java.util.Date;

import static java.util.Comparator.comparing;

public class DirectoryEntry extends BaseNodeDescription implements Comparable<DirectoryEntry> {

  public static final Comparator<DirectoryEntry> CASE_INSENSITIVE_ORDER =
    comparing(DirectoryEntry::getKind).thenComparing(entry -> entry.getUrl().toDecodedString(), String.CASE_INSENSITIVE_ORDER);

  private final String myName;
  @NotNull private final CommitInfo myCommitInfo;
  private final String myPath;
  private final Url myUrl;
  private final Url myRepositoryRoot;

  public DirectoryEntry(Url url,
                        Url repositoryRoot,
                        String name,
                        @NotNull NodeKind kind,
                        @Nullable CommitInfo commitInfo,
                        String path) {
    super(kind);
    myUrl = url;
    myRepositoryRoot = repositoryRoot;
    myName = name;
    myCommitInfo = ObjectUtils.notNull(commitInfo, CommitInfo.EMPTY);
    myPath = path;
  }

  public Url getUrl() {
    return myUrl;
  }

  public Url getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public String getName() {
    return myName;
  }

  @NotNull
  public NodeKind getKind() {
    return myKind;
  }

  public Date getDate() {
    return myCommitInfo.getDate();
  }

  public long getRevision() {
    return myCommitInfo.getRevision();
  }

  public String getAuthor() {
    return myCommitInfo.getAuthor();
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
