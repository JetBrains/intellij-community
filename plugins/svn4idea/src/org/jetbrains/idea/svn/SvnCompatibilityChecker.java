// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Service
final class SvnCompatibilityChecker {
  private final Project myProject;
  private final static long ourFrequency = 10;
  private final static long ourInvocationMax = 10;
  private final static long ourInitCounter = 3;

  private long myCounter = 0;
  private long myDownStartCounter = ourInitCounter;
  private long myInvocationCounter = 0;

  private final Object myLock = new Object();

  SvnCompatibilityChecker(@NotNull Project project) {
    myProject = project;
  }

  public void checkAndNotify(@NotNull List<VirtualFile> roots) {
    if (roots.isEmpty()) return;

    synchronized (myLock) {
      if (myInvocationCounter >= ourInvocationMax) return;
      ++myCounter;
      --myDownStartCounter;
      if ((myCounter > ourFrequency) || (myDownStartCounter >= 0)) {
        myCounter = 0;
        ++myInvocationCounter;
        final Application application = ApplicationManager.getApplication();
        application.executeOnPooledThread(() -> {
          final List<VirtualFile> suspicious = new ArrayList<>();
          for (VirtualFile vf : roots) {
            if (SvnUtil.seemsLikeVersionedDir(vf)) {
              suspicious.add(vf);
            }
          }
          if (!suspicious.isEmpty()) {
            final String message = (suspicious.size() == 1)
                                   ? "Root '" + suspicious.get(0).getPresentableName() + "' is likely to be of unsupported Subversion format"
                                   : "Some roots are likely to be of unsupported Subversion format";
            application
              .invokeLater(() -> new VcsBalloonProblemNotifier(myProject, message, MessageType.WARNING).run(), ModalityState.NON_MODAL,
                           o -> (!myProject.isOpen()) || myProject.isDisposed());
          }
        });
      }
    }
  }
}
