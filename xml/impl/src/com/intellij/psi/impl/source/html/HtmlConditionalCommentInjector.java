package com.intellij.psi.impl.source.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author spleaner
 */
public class HtmlConditionalCommentInjector implements MultiHostInjector {
  public void getLanguagesToInject(@NotNull final MultiHostRegistrar registrar, @NotNull final PsiElement host) {
    if (host instanceof XmlComment) {
      final ASTNode comment = host.getNode();
      if (comment != null) {
        final ASTNode[] conditionalStarts = comment.getChildren(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_START_END));
        if (conditionalStarts.length > 0) {
          final ASTNode[] conditionalEnds = comment.getChildren(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_END_START));
          if (conditionalEnds.length > 0) {
            final ASTNode[] endOfEnd = comment.getChildren(TokenSet.create(XmlTokenType.XML_CONDITIONAL_COMMENT_END));
            if (endOfEnd.length > 0) {
              final TextRange textRange = host.getTextRange();
              final int startOffset = textRange.getStartOffset();

              final ASTNode start = conditionalStarts[0];
              final ASTNode end = conditionalEnds[0];
              registrar.startInjecting(HTMLLanguage.INSTANCE).addPlace(null, null, (PsiLanguageInjectionHost)host,
                                                                       new TextRange(start.getTextRange().getEndOffset() - startOffset,
                                                                                     end.getStartOffset() - startOffset)).doneInjecting();
            }
          }
        }
      }
    }


  }


  @NotNull
  public List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Arrays.asList(PsiComment.class);
  }
}
