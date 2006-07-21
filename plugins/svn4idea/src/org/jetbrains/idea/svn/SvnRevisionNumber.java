/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 08.06.2005
 * Time: 22:29:32
 * To change this template use File | Settings | File Templates.
 */
public class SvnRevisionNumber implements VcsRevisionNumber {
  @NotNull
  private SVNRevision myRevision;

  public SvnRevisionNumber(@NotNull SVNRevision revision) {
    myRevision = revision;
  }

  public String asString() {
    return myRevision.toString();
  }

  public int compareTo(VcsRevisionNumber vcsRevisionNumber) {
    if (vcsRevisionNumber == null || vcsRevisionNumber.getClass() != SvnRevisionNumber.class) {
      return -1;
    }
    SVNRevision rev = ((SvnRevisionNumber)vcsRevisionNumber).myRevision;
    if (!myRevision.isValid()) {
      return !rev.isValid() ? 0 : -1;
    }
    if (myRevision.getNumber() >= 0 && rev.getNumber() >= 0) {
      return myRevision.getNumber() == rev.getNumber() ? 0 : myRevision.getNumber() > rev.getNumber() ? 1 : -1;
    }
    else if (myRevision.getDate() != null && rev.getDate() != null) {
      return myRevision.getDate().compareTo(rev.getDate());
    }
    return myRevision.getID() == rev.getID() ? 0 : myRevision.getID() > rev.getID() ? 1 : -1;
  }

  public SVNRevision getRevision() {
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
