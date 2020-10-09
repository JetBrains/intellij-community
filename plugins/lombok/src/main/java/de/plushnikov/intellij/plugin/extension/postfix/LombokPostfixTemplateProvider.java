package de.plushnikov.intellij.plugin.extension.postfix;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.JavaCompletionContributor;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class LombokPostfixTemplateProvider implements PostfixTemplateProvider {

  private final Set<PostfixTemplate> lombokTemplates = new HashSet<>();

  public LombokPostfixTemplateProvider() {
    lombokTemplates.add(new LombokValPostfixTemplate());
    lombokTemplates.add(new LombokVarPostfixTemplate());
  }

  @NotNull
  @Override
  public Set<PostfixTemplate> getTemplates() {
    return lombokTemplates;
  }

  @Override
  public boolean isTerminalSymbol(char currentChar) {
    return currentChar == '.' || currentChar == '!';
  }

  @Override
  public void preExpand(@NotNull PsiFile file, @NotNull Editor editor) {
  }

  @Override
  public void afterExpand(@NotNull PsiFile file, @NotNull Editor editor) {
  }

  @NotNull
  @Override
  public PsiFile preCheck(@NotNull PsiFile copyFile, @NotNull Editor realEditor, int currentOffset) {
    Document document = copyFile.getViewProvider().getDocument();
    assert document != null;
    CharSequence sequence = document.getCharsSequence();
    StringBuilder fileContentWithSemicolon = new StringBuilder(sequence);
    if (isSemicolonNeeded(copyFile, realEditor)) {
      fileContentWithSemicolon.insert(currentOffset, ';');
      return PostfixLiveTemplate.copyFile(copyFile, fileContentWithSemicolon);
    }

    return copyFile;
  }

  private static boolean isSemicolonNeeded(@NotNull PsiFile file, @NotNull Editor editor) {
    int startOffset = CompletionInitializationContext.calcStartOffset(editor.getCaretModel().getCurrentCaret());
    return JavaCompletionContributor.semicolonNeeded(editor, file, startOffset);
  }

}
