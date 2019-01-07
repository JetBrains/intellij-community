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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.instanceOf;

public class BashCompletionContributor extends CompletionContributor implements DumbAware {
  public BashCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inFile(instanceOf(BashFile.class)), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters, @NotNull ProcessingContext context, @NotNull CompletionResultSet result) {
        for (String keywords : suggestKeywords(parameters.getPosition())) {
          result.addElement(LookupElementBuilder.create(keywords).bold().withInsertHandler(AddSpaceInsertHandler.INSTANCE));
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
        if (o instanceof IElementType[] && ((IElementType[]) o).length > 0) return kw2str(((IElementType[]) o)[0]);
        return o instanceof IElementType ? kw2str(((IElementType) o)) : null;
      }

      @Nullable
      private String kw2str(IElementType o) {
        return BashLexer.HUMAN_READABLE_KEYWORDS.contains(o) ? o.toString() : null;
      }
    };
    file.putUserData(GeneratedParserUtilBase.COMPLETION_STATE_KEY, state);
    TreeUtil.ensureParsed(file.getNode());
    return state.items;
  }
}
