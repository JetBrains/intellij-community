// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import com.jetbrains.performancePlugin.utils.ActionCallbackProfilerStopper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

public class MoveDirectoryCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "moveDirectory";
  private static final Logger LOG = Logger.getInstance(MoveDirectoryCommand.class);

  public MoveDirectoryCommand(@NotNull String text, int line) {
    super(text, line, true);
  }

  @Override
  protected @NotNull Promise<Object> _execute(final @NotNull PlaybackContext context) {
    final ActionCallback actionCallback = new ActionCallbackProfilerStopper();
    Project project = context.getProject();
    PsiManagerImpl myPsiManager = (PsiManagerImpl)PsiManager.getInstance(project);
    String input = extractCommandArgument(PREFIX);
    String[] lineAndColumn = input.split(" ");
    final String sourcePath = lineAndColumn[0];
    final String targetPath = lineAndColumn[1];
    VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    if(projectDir != null) {
      VirtualFile sourceVirtualFile = projectDir.findFileByRelativePath(sourcePath);
      VirtualFile targetVirtualFile = projectDir.findFileByRelativePath(targetPath);
      if(sourceVirtualFile != null && targetVirtualFile != null) {
        final PsiDirectory sourcePsiDir = myPsiManager.findDirectory(sourceVirtualFile);
        final PsiDirectory targetPsiDir = myPsiManager.findDirectory(targetVirtualFile);

        ApplicationManager.getApplication().invokeAndWait(() ->
                                                            WriteCommandAction.writeCommandAction(project).run(() -> {
                                                              MoveFilesOrDirectoriesUtil.doMoveDirectory(sourcePsiDir, targetPsiDir);
                                                              LOG.info("Dir " + sourcePath + " has been moved to " + targetPath);
                                                              actionCallback.setDone();
                                                            }), ModalityState.nonModal());
      } else {
        actionCallback.reject("Source or target dir is not found");
      }
    } else {
      actionCallback.reject("Project dir can't be guessed");
    }
    return Promises.toPromise(actionCallback);
  }
}
