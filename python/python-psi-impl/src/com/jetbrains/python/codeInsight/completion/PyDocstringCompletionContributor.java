// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.documentation.docstrings.DocStringTagCompletionContributor;
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.patterns.PlatformPatterns.psiComment;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * User : ktisha
 */
@ApiStatus.Internal
public final class PyDocstringCompletionContributor extends CompletionContributor implements DumbAware {
  public PyDocstringCompletionContributor() {
    extend(CompletionType.BASIC,
           or(psiElement().withParent(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN), psiComment()),
           new IdentifierCompletionProvider());
  }

  private static class IdentifierCompletionProvider extends CompletionProvider<CompletionParameters> {

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
      final PsiElement element = parameters.getOriginalPosition();
      if (element == null) return;
      final PsiFile file = element.getContainingFile();
      // Parameter references are filled with DocStringParameterReference#getVariants
      final PsiReference reference = file.findReferenceAt(parameters.getOffset());
      if (reference == null) {
        if (parameters.isAutoPopup()) return;
        final PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
        final Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
          result = result.withPrefixMatcher(getPrefix(parameters.getOffset(), file));
          final Collection<String> identifiers = PyUtil.collectUsedNames(docStringOwner);
          for (String identifier : identifiers) {
            result.addElement(LookupElementBuilder.create(identifier).withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
          }

          final Collection<String> fileIdentifiers = PyUtil.collectUsedNames(parameters.getOriginalFile());
          for (String identifier : fileIdentifiers) {
            result.addElement(LookupElementBuilder.create(identifier).withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
          }
        }
      }
      // For reST declaration like ":param foo: int" "foo" is PsiMultiReference that combines DocStringParameterReference and DocStringTypeReference
      else if (reference instanceof PsiMultiReference) {
        for (PsiReference innerReference : ((PsiMultiReference)reference).getReferences()) {
          addVariantsFromDocstringReference(innerReference, result);
        }
      }
      else {
        addVariantsFromDocstringReference(reference, result);
      }
    }

    private static void addVariantsFromDocstringReference(@NotNull PsiReference reference, @NotNull CompletionResultSet result) {
      if (reference instanceof DocStringParameterReference) {
        for (PyNamedParameter param : ((DocStringParameterReference)reference).collectParameterVariants()) {
          result.addElement(LookupElementBuilder.createWithIcon(param));
        }
      }
      else if (reference instanceof DocStringTypeReference) {
        for (Object variant : ((DocStringTypeReference)reference).collectTypeVariants()) {
          if (variant instanceof PsiNamedElement) {
            result.addElement(LookupElementBuilder.createWithIcon((PsiNamedElement)variant));
          }
          else {
            result.addElement(LookupElementBuilder.create(variant));
          }
        }
      }
    }
  }

  private static String getPrefix(int offset, PsiFile file) {
    if (offset > 0) {
      offset--;
    }
    final String text = file.getText();
    StringBuilder prefixBuilder = new StringBuilder();
    while (offset > 0 && Character.isLetterOrDigit(text.charAt(offset))) {
      prefixBuilder.insert(0, text.charAt(offset));
      offset--;
    }
    return prefixBuilder.toString();
  }

  @Override
  public void beforeCompletion(@NotNull CompletionInitializationContext context) {
    // With standard dummy identifier inserted, docstring might become malformed
    // e.g. "@param para<caret>m" -> "@param paraIntellijIdeaRulezzz m"
    // and param is no longer parameter, but type reference now
    final PsiReference ref = context.getFile().findReferenceAt(context.getCaret().getOffset());
    if (ref instanceof DocStringParameterReference || ref instanceof DocStringTypeReference) {
      context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
    }
  }
}
