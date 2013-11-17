package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.*;
import com.intellij.codeInsight.completion.scope.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.psi.*;
import com.intellij.psi.filters.*;
import com.intellij.psi.impl.source.resolve.reference.impl.*;
import com.intellij.psi.infos.*;
import com.intellij.psi.search.*;
import com.intellij.util.*;
import com.intellij.util.containers.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.LinkedHashSet;

public abstract class PostfixNoVariantsCompletionUtil {


  @NotNull public static Set<LookupElement> suggestQualifierItems(
    @NotNull CompletionParameters parameters, @NotNull PsiJavaCodeReferenceElement qualifier) {

    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) return Collections.emptySet();

    PrefixMatcher qualifierMatcher = new CamelHumpMatcher(referenceName);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(parameters.getPosition());
    if (filter == null) return Collections.emptySet();

    Set<LookupElement> variants = completeReference(
      qualifier, qualifier, filter, parameters, qualifierMatcher);

    // add type names available in context
    PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(qualifier.getProject());
    for (PsiClass aClass : namesCache.getClassesByName(referenceName, qualifier.getResolveScope())) {
      variants.add(JavaClassNameCompletionContributor.createClassLookupItem(aClass, true));
    }

    if (!variants.isEmpty()) return variants;

    // add import items (types)
    Set<LookupElement> allClasses = new LinkedHashSet<LookupElement>();
    CompletionParameters qualifierParameters = parameters.withPosition(
      qualifier.getReferenceNameElement(), qualifier.getTextRange().getEndOffset());
    JavaClassNameCompletionContributor.addAllClasses(
      qualifierParameters, true, qualifierMatcher, new CollectConsumer<LookupElement>(allClasses));

    return allClasses;
  }

  @NotNull private static Set<LookupElement> completeReference(
    @NotNull PsiElement element, PsiReference reference, @NotNull ElementFilter filter,
    @NotNull CompletionParameters parameters, @NotNull PrefixMatcher matcher) {

    if (reference instanceof PsiMultiReference) {
      PsiReference[] references = ((PsiMultiReference) reference).getReferences();
      reference = ContainerUtil.findInstance(references, PsiJavaReference.class);
    }

    if (reference instanceof PsiJavaReference) {
      JavaCompletionProcessor.Options options = JavaCompletionProcessor.Options
        .DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1);

      MyElementFilter elementFilter = new MyElementFilter(filter);
      return JavaCompletionUtil.processJavaReference(
        element, (PsiJavaReference) reference, elementFilter, options, matcher, parameters);
    }

    return Collections.emptySet();
  }

  private static class MyElementFilter implements ElementFilter {
    @NotNull private final ElementFilter myFilter;
    public MyElementFilter(@NotNull ElementFilter filter) { myFilter = filter; }

    @Override public boolean isAcceptable(Object element, PsiElement context) {
      return myFilter.isAcceptable(element, context);
    }

    @Override public boolean isClassAcceptable(Class hintClass) {
      return ReflectionCache.isAssignable(PsiClass.class, hintClass) ||
             ReflectionCache.isAssignable(PsiVariable.class, hintClass) ||
             ReflectionCache.isAssignable(PsiMethod.class, hintClass) ||
             ReflectionCache.isAssignable(CandidateInfo.class, hintClass);
    }
  }
}
