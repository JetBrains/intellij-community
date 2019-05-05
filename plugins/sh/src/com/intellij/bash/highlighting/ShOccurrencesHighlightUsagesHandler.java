package com.intellij.bash.highlighting;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ShOccurrencesHighlightUsagesHandler extends HighlightUsagesHandlerBase<PsiElement> {
  ShOccurrencesHighlightUsagesHandler(@NotNull Editor editor, @NotNull PsiFile file) {
    super(editor, file);
  }

  @Override
  public List<PsiElement> getTargets() {
    return Collections.singletonList(myFile);
  }

  @Override
  protected void selectTargets(List<PsiElement> targets, Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(List<PsiElement> targets) {
    TextRange textRange = ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(myEditor);
    if (textRange != null) {
      CharSequence documentText = myEditor.getDocument().getImmutableCharSequence();
      boolean hasSelection = myEditor.getCaretModel().getPrimaryCaret().hasSelection();
      List<TextRange> occurrences = ShTextOccurrencesUtil.findAllOccurrences(
          documentText,
          textRange.subSequence(documentText),
          !hasSelection);
      myReadUsages.addAll(occurrences);
    }
  }
}
