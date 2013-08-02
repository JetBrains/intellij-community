package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.refactoring.PyRefactoringUtil;

import java.util.Collection;

/**
 * User : ktisha
 */
public class PyDocstringCompletionContributor extends CompletionContributor {

  public void fillCompletionVariants(final CompletionParameters parameters, CompletionResultSet result) {
    final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(parameters.getOriginalPosition(), PyDocStringOwner.class);
    if (docStringOwner != null) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(docStringOwner);
      if (module != null) {
        final PsiFile file = docStringOwner.getContainingFile();
        result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), file));
        final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
        if (!settings.isPlain(file)) return;
        final Collection<String> identifiers = PyRefactoringUtil.collectUsedNames(docStringOwner);
        for (String identifier : identifiers)
          result.addElement(LookupElementBuilder.create(identifier));

        final Collection<String> fileIdentifiers = PyRefactoringUtil.collectUsedNames(parameters.getOriginalFile());
        for (String identifier : fileIdentifiers)
          result.addElement(LookupElementBuilder.create(identifier));
      }
    }
  }

  private static String getPrefix(int offset, PsiFile file) {
    if (offset > 0) {
      offset--;
    }
    final String text = file.getText();
    StringBuilder prefixBuilder = new StringBuilder();
    while(offset > 0 && Character.isLetterOrDigit(text.charAt(offset))) {
      prefixBuilder.insert(0, text.charAt(offset));
      offset--;
    }
    return prefixBuilder.toString();
  }
}
