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
import org.tmatesoft.svn.core.wc.SVNConflictAction;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/23/12
 * Time: 7:09 PM
 */
public class ConflictActionConvertor {
  public static SVNConflictAction create(final ConflictDescriptor conflict) {
    return create(conflict.getAction());
  }

  public static SVNConflictAction create(ConflictDescriptor.Action action) {
    if (ConflictDescriptor.Action.add.equals(action)) {
      return SVNConflictAction.ADD;
    } else if (ConflictDescriptor.Action.delete.equals(action)) {
      return SVNConflictAction.DELETE;
    } else if (ConflictDescriptor.Action.edit.equals(action)) {
      return SVNConflictAction.EDIT;
    }
    throw new IllegalStateException();
  }
}
