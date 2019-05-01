package com.intellij.bash.highlighting;

import com.intellij.bash.psi.BashFile;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BashOccurrencesHighlightingFactory extends HighlightUsagesHandlerFactoryBase {
  @Nullable
  @Override
  public HighlightUsagesHandlerBase createHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file, @NotNull PsiElement target) {
    if (editor.isOneLineMode() ||
        !CodeInsightSettings.getInstance().HIGHLIGHT_IDENTIFIER_UNDER_CARET ||
        !(file instanceof BashFile)
    ) {
      return null;
    }
    return new BashOccurrencesHighlightUsagesHandler(editor, file);
  }
}
