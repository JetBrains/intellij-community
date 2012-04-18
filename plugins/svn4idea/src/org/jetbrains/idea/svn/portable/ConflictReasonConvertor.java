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

import org.apache.subversion.javahl.ConflictDescriptor;
import org.tmatesoft.svn.core.wc.SVNConflictReason;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/23/12
 * Time: 7:26 PM
 */
public class ConflictReasonConvertor {
  public static SVNConflictReason convert(ConflictDescriptor.Reason reason) {
    if (ConflictDescriptor.Reason.added.equals(reason)) {
      return SVNConflictReason.ADDED;
    } else if (ConflictDescriptor.Reason.deleted.equals(reason)) {
      return SVNConflictReason.DELETED;
    } else if (ConflictDescriptor.Reason.edited.equals(reason)) {
      return SVNConflictReason.EDITED;
    } else if (ConflictDescriptor.Reason.missing.equals(reason)) {
      return SVNConflictReason.MISSING;
    } else if (ConflictDescriptor.Reason.obstructed.equals(reason)) {
      return SVNConflictReason.OBSTRUCTED;
    } else if (ConflictDescriptor.Reason.unversioned.equals(reason)) {
      return SVNConflictReason.UNVERSIONED;
    }
    throw new IllegalStateException();
  }
}
