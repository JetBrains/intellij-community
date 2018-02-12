/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.history.LongRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Revision;

/**
 * @author alex
 */
public class SvnRevisionNumber implements VcsRevisionNumber, LongRevisionNumber {
  @NotNull
  private final Revision myRevision;

  public SvnRevisionNumber(@NotNull Revision revision) {
    myRevision = revision;
  }

  public String asString() {
    return myRevision.toString();
  }

  @Override
  public long getLongRevisionNumber() {
    return myRevision.getNumber();
  }

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

  @NotNull
  public Revision getRevision() {
    return myRevision;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SvnRevisionNumber that = (SvnRevisionNumber)o;

    return myRevision.equals(that.myRevision);

  }

  public int hashCode() {
    return myRevision.hashCode();
  }

  public String toString() {
    return myRevision.toString();
  }
}
