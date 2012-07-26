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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.ShowAllAffectedGenericAction;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.SvnEntriesListener;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;

public class SvnFileAnnotation extends BaseSvnFileAnnotation {
  private final VirtualFile myFile;
  private final SvnEntriesListener myListener = new SvnEntriesListener() {
    public void onEntriesChanged(VirtualFile directory) {
      if (!Comparing.equal(directory, myFile.getParent())) return;
      final VcsRevisionNumber currentRevision = myVcs.getDiffProvider().getCurrentRevision(myFile);
      if (currentRevision != null && currentRevision.equals(myBaseRevision)) return;

      final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].onAnnotationChanged();
      }
    }

    @Override
    public void fileVersionProbablyChanged(VirtualFile file) {
      if (myFile.equals(file)) {
        final VcsRevisionNumber currentRevision = myVcs.getDiffProvider().getCurrentRevision(myFile);
        if (currentRevision != null && currentRevision.equals(myBaseRevision)) return;

        final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
        for (int i = 0; i < listeners.length; i++) {
          listeners[i].onAnnotationChanged();
        }
      }
    }
  };

  public SvnFileAnnotation(SvnVcs vcs, VirtualFile file, String contents, VcsRevisionNumber baseRevision) {
    super(vcs, contents, baseRevision);
    myFile = file;
    myVcs.addEntriesListener(myListener);
  }

  public void dispose() {
    myVcs.removeEntriesListener(myListener);
  }

  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  protected void showAllAffectedPaths(SvnRevisionNumber number) {
    ShowAllAffectedGenericAction.showSubmittedFiles(myVcs.getProject(), number, myFile, myVcs.getKeyInstanceMethod());
  }
}
