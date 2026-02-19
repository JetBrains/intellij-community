// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console.completion;

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author oleg
 */
public final class PythonConsoleAutopopupBlockingHandler extends TypedHandlerDelegate {

  public static final Key<Object> REPL_KEY = new Key<>("python.repl.console.editor");

  @Override
  public @NotNull Result checkAutoPopup(final char charTyped, final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile file) {
    if (editor.getUserData(REPL_KEY) != null){
      return Result.DEFAULT;
    }
    return Result.CONTINUE;
  }
}
