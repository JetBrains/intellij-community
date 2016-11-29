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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class SvnCompatibilityChecker {
  private final Project myProject;
  private final static long ourFrequency = 10;
  private final static long ourInvocationMax = 10;
  private final static long ourInitCounter = 3;
  
  private long myCounter;
  private long myDownStartCounter;
  private long myInvocationCounter;

  private final Object myLock = new Object();

  public SvnCompatibilityChecker(Project project) {
    myProject = project;
    myCounter= 0;
    myDownStartCounter = ourInitCounter;
    myInvocationCounter = 0;
  }

  public void reportNoRoots(final List<VirtualFile> result) {
    synchronized (myLock) {
      if (myInvocationCounter >= ourInvocationMax) return;
      ++ myCounter;
      -- myDownStartCounter;
      if ((myCounter > ourFrequency) || (myDownStartCounter >= 0)) {
        myCounter = 0;
        ++ myInvocationCounter;
        final Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(new Runnable() {
          public void run() {
            final List<VirtualFile> suspicious = new ArrayList<>();
            for (VirtualFile vf : result) {
              if (SvnUtil.seemsLikeVersionedDir(vf)) {
                suspicious.add(vf);
              }
            }
            if (! suspicious.isEmpty()) {
              final String message = (suspicious.size() == 1) ?
                                     "Root '" + suspicious.get(0).getPresentableName() + "' is likely to be of unsupported Subversion format" :
                                     "Some roots are likely to be of unsupported Subversion format";
              application.invokeLater(new Runnable() {
                public void run() {
                  new VcsBalloonProblemNotifier(myProject, message, MessageType.WARNING).run();
                }
              }, ModalityState.NON_MODAL, new Condition() {
                @Override
                public boolean value(Object o) {
                  return (! myProject.isOpen()) || myProject.isDisposed();
                }
              });
            }
          }
        });
      }
    }
  }
}
