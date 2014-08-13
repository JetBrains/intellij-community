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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Depth;

public class SvnExcludingIgnoredOperation {
  private final Operation myImportAction;
  private final Depth myDepth;
  private final Filter myFilter;

  public SvnExcludingIgnoredOperation(final Project project, final Operation importAction, final Depth depth) {
    myImportAction = importAction;
    myDepth = depth;

    myFilter = new Filter(project);
  }

  public static class Filter {
    private final Project myProject;
    private final ProjectLevelVcsManager myVcsManager;
    private final ChangeListManager myClManager;

    public Filter(final Project project) {
      myProject = project;

      if (!project.isDefault()) {
        myVcsManager = ProjectLevelVcsManager.getInstance(project);
        myClManager = ChangeListManager.getInstance(project);
      }
      else {
        myVcsManager = null;
        myClManager = null;
      }
    }

    public boolean accept(final VirtualFile file) {
      if (!myProject.isDefault()) {
        if (isIgnoredByVcs(file) || myClManager.isIgnoredFile(file)) {
          return false;
        }
      }
      return true;
    }

    private boolean isIgnoredByVcs(final VirtualFile file) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return myVcsManager.isIgnored(file);
        }
      });
    }
  }

  private boolean operation(final VirtualFile file) throws VcsException {
    if (!myFilter.accept(file)) return false;

    myImportAction.doOperation(file);
    return true;
  }

  private void executeDown(final VirtualFile file) throws VcsException {
    VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        try {
          return operation(file);
        }
        catch (VcsException e) {
          throw new VisitorException(e);
        }
      }
    }, VcsException.class);
  }

  public void execute(final VirtualFile file) throws VcsException {
    if (Depth.INFINITY.equals(myDepth)) {
      executeDown(file);
      return;
    }

    if (!operation(file)) {
      return;
    }

    if (Depth.EMPTY.equals(myDepth)) {
      return;
    }

    for (VirtualFile child : file.getChildren()) {
      if (Depth.FILES.equals(myDepth) && child.isDirectory()) {
        continue;
      }
      operation(child);
    }
  }

  public interface Operation {
    void doOperation(final VirtualFile file) throws VcsException;
  }
}
