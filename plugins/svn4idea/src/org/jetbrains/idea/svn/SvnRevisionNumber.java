// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.history.LongRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Revision;

/**
 * @author alex
 */
public class SvnRevisionNumber implements VcsRevisionNumber, LongRevisionNumber {
  private final @NotNull Revision myRevision;

  public SvnRevisionNumber(@NotNull Revision revision) {
    myRevision = revision;
  }

  @Override
  public @NotNull String asString() {
    return myRevision.toString();
  }

  @Override
  public long getLongRevisionNumber() {
    return myRevision.getNumber();
  }

  @Override
  public int compareTo(VcsRevisionNumber vcsRevisionNumber) {
    if (vcsRevisionNumber == null || vcsRevisionNumber.getClass() != SvnRevisionNumber.class) {
      return -1;
    }
    Revision rev = ((SvnRevisionNumber)vcsRevisionNumber).myRevision;

    if (myRevision.getNumber() >= 0 && rev.getNumber() >= 0) {
      return java.lang.Long.compare(myRevision.getNumber(), rev.getNumber());
    }
    else if (myRevision.getDate() != null && rev.getDate() != null) {
      return myRevision.getDate().compareTo(rev.getDate());
    }
    return Revision.GENERAL_ORDER.compare(myRevision, rev);
  }

  public @NotNull Revision getRevision() {
    return myRevision;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnRevisionNumber that = (SvnRevisionNumber)o;

    return myRevision.equals(that.myRevision);

  }

  @Override
  public int hashCode() {
    return myRevision.hashCode();
  }

  @Override
  public String toString() {
    return myRevision.toString();
  }
}
