// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.progress.ProgressManager.checkCanceled;
import static com.intellij.openapi.progress.util.BackgroundTaskUtil.executeOnPooledThread;
import static com.intellij.openapi.util.Disposer.isDisposed;
import static com.intellij.util.containers.ContainerUtil.filter;
import static org.jetbrains.idea.svn.SvnBundle.message;

@Service
final class SvnCompatibilityChecker implements Disposable {
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
      if (myCounter <= ourFrequency && myDownStartCounter < 0) return;

      myCounter = 0;
      ++myInvocationCounter;
    }

    executeOnPooledThread(this, () -> {
      List<VirtualFile> incompatible = filter(roots, SvnCompatibilityChecker::seemsVersioned);
      if (!incompatible.isEmpty()) notify(incompatible);
    });
  }

  @Override
  public void dispose() {
  }

  private void notify(@NotNull List<VirtualFile> roots) {
    String message = roots.size() == 1
                     ? message("notification.content.single.root.unsupported.format", roots.get(0).getPresentableName())
                     : message("notification.content.multiple.roots.unsupported.format");

    getApplication().invokeLater(
      () -> {
        if (isDisposed(this)) return;
        new VcsBalloonProblemNotifier(myProject, message, MessageType.WARNING).run();
      },
      ModalityState.NON_MODAL
    );
  }

  private static boolean seemsVersioned(@NotNull VirtualFile root) {
    checkCanceled();
    return SvnUtil.seemsLikeVersionedDir(root);
  }
}
