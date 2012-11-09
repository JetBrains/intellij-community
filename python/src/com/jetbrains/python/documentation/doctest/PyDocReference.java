package com.jetbrains.python.documentation.doctest;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.ResolveResultList;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User : ktisha
 */
public class PyDocReference extends PyReferenceImpl {
  public PyDocReference(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }


  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    ResolveResult[] results = super.multiResolve(incompleteCode);
    if (results.length == 0) {
      final ResolveResultList ret = new ResolveResultList();
      PsiFile file = myElement.getContainingFile();
      final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
      final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
      if (host != null) file = host.getContainingFile();
      final String referencedName = myElement.getReferencedName();
      if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

      if (host != null) {
        final List<Pair<PsiElement,TextRange>> files = languageManager.getInjectedPsiFiles(host);
        if (files != null) {
          for (Pair<PsiElement, TextRange> pair : files) {
            ResolveProcessor processor = new ResolveProcessor(referencedName);

            PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)pair.getFirst(), referencedName, pair.getFirst());
            PsiElement uexpr = processor.getResult();
            if (uexpr != null) ret.add(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, uexpr));
            if (ret.size() > 0) {
              return ret.toArray(new RatedResolveResult[ret.size()]);
            }
          }
        }
      }

      ResolveProcessor processor = new ResolveProcessor(referencedName);

      PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)file, referencedName, file);
      PsiElement uexpr = processor.getResult();
      if (uexpr != null) ret.add(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, uexpr));
      if (ret.size() > 0) {
        return ret.toArray(new RatedResolveResult[ret.size()]);
      }
    }
    return results;
  }

  @NotNull
  public Object[] getVariants() {
    final ArrayList<Object> ret = Lists.newArrayList(super.getVariants());
    PsiFile file = myElement.getContainingFile();
    final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
    final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
    if (host != null) file = host.getContainingFile();

    final PsiElement originalElement = CompletionUtil.getOriginalElement(myElement);
    final PyQualifiedExpression element = originalElement instanceof PyQualifiedExpression ?
                                          (PyQualifiedExpression)originalElement : myElement;

    // include our own names
    final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element);
    PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner)file, null, null);

    ret.addAll(processor.getResultList());

    return ret.toArray();
  }
}
