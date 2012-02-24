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
package org.jetbrains.idea.svn.portable;

import org.apache.subversion.javahl.types.Revision;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 6:59 PM
 */
public class RevisionConvertor {
  public static Revision convert(@NotNull SVNRevision r) {
    if (SVNRevision.BASE.equals(r)) {
      return Revision.BASE;
    } else if (SVNRevision.COMMITTED.equals(r)) {
      return Revision.COMMITTED;
    } else if (SVNRevision.HEAD.equals(r)) {
      return Revision.HEAD;
    } else if (SVNRevision.PREVIOUS.equals(r)) {
      return Revision.PREVIOUS;
    } else if (SVNRevision.UNDEFINED.equals(r)) {
      return Revision.START;
    } else if (SVNRevision.WORKING.equals(r)) {
      return Revision.WORKING;
    } else {
      return new Revision.Number(r.getNumber());
    }
  }

  public static SVNRevision convert(Revision r) {
    if (r == null) return null;
    if (Revision.BASE.equals(r)) {
      return SVNRevision.BASE;
    } else if (Revision.COMMITTED.equals(r)) {
      return SVNRevision.COMMITTED;
    } else if (Revision.HEAD.equals(r)) {
      return SVNRevision.HEAD;
    } else if (Revision.PREVIOUS.equals(r)) {
      return SVNRevision.PREVIOUS;
    } else if (Revision.START.equals(r)) {
      return SVNRevision.UNDEFINED;
    } else if (Revision.WORKING.equals(r)) {
      return SVNRevision.WORKING;
    } else {
      if (Revision.Kind.number.equals(r.getKind())) {
        return SVNRevision.create(((Revision.Number)r).getNumber());
      }
      else {
        return SVNRevision.create(((Revision.DateSpec)r).getDate());
      }
    }
  }
}
