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

import com.intellij.lifecycle.PeriodicalTasksCloser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNStatusFileProvider;

public class StatusWalkerPartner {
  private final SvnVcs myVcs;
  private final ChangeListManager myClManager;
  private final FileIndexFacade myExcludedFileIndex;
  private final ProgressIndicator myIndicator;
  private ISVNStatusFileProvider myFileProvider;

  public StatusWalkerPartner(final SvnVcs vcs, final ProgressIndicator pi) {
    myVcs = vcs;
    myClManager = ChangeListManager.getInstance(myVcs.getProject());
    myExcludedFileIndex = PeriodicalTasksCloser.getInstance().safeGetService(myVcs.getProject(), FileIndexFacade.class);
    myIndicator = pi;
  }

  public void setFileProvider(final ISVNStatusFileProvider fileProvider) {
    myFileProvider = fileProvider;
  }

  @NotNull
  public ProgressTracker getEventHandler() {
    return new ProgressTracker() {
      public void consume(ProgressEvent event) throws SVNException {
        //
      }

      public void checkCancelled() throws SVNCancelException {
        if (myIndicator != null) {
          myIndicator.checkCanceled();
        }
      }
    };
  }

  @Nullable
  public ISVNStatusFileProvider getFileProvider() {
    return myFileProvider;
  }

  public void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }

  public boolean isExcluded(final VirtualFile vFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        if (myVcs.getProject().isDisposed()) throw new ProcessCanceledException();
        return myExcludedFileIndex.isExcludedFile(vFile);
      }
    });
  }

  public boolean isIgnoredIdeaLevel(VirtualFile vFile) {
    return myClManager.isIgnoredFile(vFile);
  }
}
