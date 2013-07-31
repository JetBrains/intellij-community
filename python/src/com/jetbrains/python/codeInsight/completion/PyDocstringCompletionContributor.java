package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * User : ktisha
 */
public class PyDocstringCompletionContributor extends CompletionContributor {
  public PyDocstringCompletionContributor() {
    extend(CompletionType.BASIC,
           psiElement().inside(PyStringLiteralExpression.class).withElementType(PyTokenTypes.DOCSTRING),
           new IdentifierCompletionProvider());
  }

  private static class IdentifierCompletionProvider extends CompletionProvider<CompletionParameters> {

    private IdentifierCompletionProvider() {
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PyDocStringOwner.class);
      if (docStringOwner != null) {
        final Collection<String> identifiers = PyRefactoringUtil.collectUsedNames(docStringOwner);
        for (String identifier : identifiers)
          result.addElement(LookupElementBuilder.create(identifier));
      }


      final Collection<String> fileIdentifiers = PyRefactoringUtil.collectUsedNames(parameters.getOriginalFile());
      for (String identifier : fileIdentifiers)
        result.addElement(LookupElementBuilder.create(identifier));
    }
  }
}
