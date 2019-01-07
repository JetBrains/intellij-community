package com.intellij.bash.psi;

import com.intellij.bash.BashLanguage;
import com.intellij.bash.lexer.BashLexer;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.parser.GeneratedParserUtilBase;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class BashCompletionContributor extends CompletionContributor implements DumbAware {
  public BashCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(instanceOf(BashFile.class)), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        for (String keywords : suggestKeywords(parameters.getPosition())) {
          result.addElement(LookupElementBuilder.create(keywords).bold());
        }
      }
    });
  }

  private static Collection<String> suggestKeywords(PsiElement position) {
    TextRange posRange = position.getTextRange();
    BashFile posFile = (BashFile) position.getContainingFile();
    final TextRange range = new TextRange(0, posRange.getStartOffset());
    final String text = range.isEmpty() ? CompletionInitializationContext.DUMMY_IDENTIFIER : range.substring(posFile.getText());

    PsiFile file = PsiFileFactory.getInstance(posFile.getProject()).createFileFromText("a.sh", BashLanguage.INSTANCE, text, true, false);
    int completionOffset = posRange.getStartOffset() - range.getStartOffset();
    GeneratedParserUtilBase.CompletionState state = new GeneratedParserUtilBase.CompletionState(completionOffset) {
      @Override
      public String convertItem(Object o) {
        if (o instanceof IElementType && BashLexer.HUMAN_READABLE_KEYWORDS.contains((IElementType) o)) return o.toString();
        return null;
      }
    };
    file.putUserData(GeneratedParserUtilBase.COMPLETION_STATE_KEY, state);
    TreeUtil.ensureParsed(file.getNode());
    return state.items;
  }
}
