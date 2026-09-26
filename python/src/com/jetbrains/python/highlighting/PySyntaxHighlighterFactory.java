// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.highlighting;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.console.PydevConsoleRunnerUtil;
import org.jetbrains.annotations.Nullable;


public final class PySyntaxHighlighterFactory extends PySyntaxHighlighterFactoryBase {
  @Override
  protected boolean useConsoleLexer(final @Nullable Project project, final @Nullable VirtualFile virtualFile) {
    if (virtualFile == null || project == null || virtualFile instanceof VirtualFileWindow) {
      return false;
    }
    return ReadAction.compute(() -> {
      PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
      return psiFile != null && PydevConsoleRunnerUtil.isInPydevConsole(psiFile);
    });
  }
}
