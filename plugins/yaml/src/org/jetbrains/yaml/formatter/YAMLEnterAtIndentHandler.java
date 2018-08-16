// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.formatter;

import com.intellij.codeInsight.editorActions.BackspaceHandler;
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.psi.YAMLFile;

public class YAMLEnterAtIndentHandler extends EnterHandlerDelegateAdapter {
  @Override
  public Result preprocessEnter(@NotNull PsiFile file,
                                @NotNull Editor editor,
                                @NotNull Ref<Integer> caretOffset,
                                @NotNull Ref<Integer> caretAdvance,
                                @NotNull DataContext dataContext,
                                EditorActionHandler originalHandler) {
    //if (true) return Result.Continue;
    if (!(file instanceof YAMLFile)) {
      return Result.Continue;
    }

    // honor dedent (RUBY-21803)
    // The solutions is copied from com.jetbrains.python.editor.PyEnterAtIndentHandler
    if (BackspaceHandler.isWhitespaceBeforeCaret(editor)) {
      return Result.DefaultSkipIndent;
    }
    return Result.Continue;
  }
}
