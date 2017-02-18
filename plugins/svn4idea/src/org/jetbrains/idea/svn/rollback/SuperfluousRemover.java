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
package org.jetbrains.idea.svn.rollback;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
* @author Konstantin Kolosovsky.
*/
public class SuperfluousRemover {

  private final Set<File> myParentPaths;
  private final boolean myCheckBefore;

  SuperfluousRemover(boolean checkBefore) {
    myCheckBefore = checkBefore;
    myParentPaths = new HashSet<>();
  }

  protected boolean accept(@NotNull Change change) {
    ContentRevision mainRevision = myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();
    ContentRevision otherRevision = !myCheckBefore ? change.getBeforeRevision() : change.getAfterRevision();

    if (mainRevision == null || SvnRollbackEnvironment.isMoveRenameReplace(change)) {
      check(otherRevision.getFile().getIOFile());
      return true;
    }

    return false;
  }

  public void check(@NotNull final File file) {
    boolean parentAlreadyRegistered = ContainerUtil.or(myParentPaths, new Condition<File>() {
      @Override
      public boolean value(@NotNull File parentCandidate) {
        return VfsUtilCore.isAncestor(parentCandidate, file, true);
      }
    });

    if (!parentAlreadyRegistered) {
      ContainerUtil.retainAll(myParentPaths, new Condition<File>() {
        @Override
        public boolean value(@NotNull File childCandidate) {
          return !VfsUtilCore.isAncestor(file, childCandidate, true);
        }
      });

      myParentPaths.add(file);
    }
  }

  public Set<File> getParentPaths() {
    return myParentPaths;
  }
}
