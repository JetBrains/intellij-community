// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.psi.PsiManager;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexEx;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Command simulates invocation of 'Invalidate caches' action
 */
public final class CleanCaches extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "cleanCaches";

  public CleanCaches(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    WriteAction.runAndWait(() -> {
      Project project = context.getProject();
      PsiManager.getInstance(project).dropResolveCaches();
      PsiManager.getInstance(project).dropPsiCaches();
      ((StubIndexEx)StubIndex.getInstance()).cleanCaches();
      ((PersistentFSImpl)PersistentFS.getInstance()).incStructuralModificationCount();
      actionCallback.setDone();
    });
    return Promises.toPromise(actionCallback);
  }
}