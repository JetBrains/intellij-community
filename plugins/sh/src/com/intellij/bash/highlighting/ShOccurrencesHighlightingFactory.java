package com.intellij.bash.highlighting;

import com.intellij.bash.psi.ShFile;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShOccurrencesHighlightingFactory extends HighlightUsagesHandlerFactoryBase {
  @Nullable
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    return !editor.isOneLineMode() && file instanceof ShFile
        ? new ShOccurrencesHighlightUsagesHandler(editor, file)
        : null;
  }
}
