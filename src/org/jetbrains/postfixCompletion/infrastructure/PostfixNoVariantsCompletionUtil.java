package org.jetbrains.postfixCompletion.infrastructure;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher;
import com.intellij.codeInsight.completion.scope.JavaCompletionProcessor;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.CollectConsumer;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.lookupItems.PostfixChainLookupElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// todo: fix 'scn.nn' prefix matching
public abstract class PostfixNoVariantsCompletionUtil {
  public static void suggestChainedCalls(@NotNull CompletionParameters parameters,
                                         @NotNull CompletionResultSet resultSet,
                                         @NotNull PostfixExecutionContext executionContext) {
    PsiElement position = parameters.getPosition(), parent = position.getParent();
    if ((!(parent instanceof PsiJavaCodeReferenceElement))) return;
    if (executionContext.insideCodeFragment) return;

    PsiElement qualifier = ((PsiJavaCodeReferenceElement)parent).getQualifier();
    if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return;

    PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
    if (qualifierReference.isQualified()) return;

    PsiElement target = qualifierReference.resolve();
    if (target != null && !(target instanceof PsiPackage)) return;

    int startOffset = parent.getTextRange().getStartOffset(); // "prefix."
    String fullPrefix = parent.getText().substring(0, parameters.getOffset() - startOffset);

    CompletionResultSet filteredResultSet = resultSet.withPrefixMatcher(fullPrefix);

    PostfixTemplatesService templatesService = PostfixTemplatesService.getInstance();
    if (templatesService == null) {
      return;
    }
    for (LookupElement qualifierElement : suggestQualifierItems(parameters, qualifierReference)) {
      PsiType type = JavaCompletionUtil.getLookupElementType(qualifierElement);
      if (type == null || PsiType.VOID == type) continue;

      final PsiReferenceExpression mockReference =
        ReferenceExpressionCompletionContributor.createMockReference(position, type, qualifierElement);
      if (mockReference == null) continue;

      final PsiElement mockReferenceQualifier = mockReference.getQualifier();
      if (mockReferenceQualifier == null) continue;

      // todo: EXTRACT ME PLZ
      PostfixTemplateContext mockTemplateContext = new PostfixTemplateContext(
        (PsiJavaCodeReferenceElement)parent, qualifierReference, executionContext) {
        @NotNull
        @Override
        protected List<PrefixExpressionContext> buildExpressionContexts(
          @NotNull PsiElement reference, @NotNull PsiElement expression) {
          return Collections.<PrefixExpressionContext>singletonList(
            new PrefixExpressionContext(this, expression) {
              @Nullable
              @Override // mock expression's type and referenced element
              protected PsiType calculateExpressionType(@NotNull PsiElement expression) {
                return super.calculateExpressionType(mockReferenceQualifier);
              }

              @Nullable
              @Override
              protected PsiElement calculateReferencedElement(@NotNull PsiElement expression) {
                return super.calculateReferencedElement(mockReferenceQualifier);
              }
            }
          );
        }

        @NotNull
        @Override
        public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
          return context; // is it right?
        }
      };

      for (LookupElement postfixElement : templatesService.collectTemplates(mockTemplateContext)) {
        PostfixChainLookupElement chainedPostfix = new PostfixChainLookupElement(qualifierElement, postfixElement);

        PrefixMatcher prefixMatcher = new CamelHumpMatcher(fullPrefix);
        boolean b = prefixMatcher.prefixMatches(chainedPostfix); // todo: wtf?

        filteredResultSet.addElement(chainedPostfix);
      }
    }
  }

  // NOTE: this is copy & paste from IDEA CE chained code completion :((
  @NotNull
  private static Set<LookupElement> suggestQualifierItems(@NotNull CompletionParameters parameters,
                                                          @NotNull PsiJavaCodeReferenceElement qualifier) {
    String referenceName = qualifier.getReferenceName();
    if (referenceName == null) return Collections.emptySet();

    PrefixMatcher qualifierMatcher = new CamelHumpMatcher(referenceName);
    ElementFilter filter = JavaCompletionContributor.getReferenceFilter(parameters.getPosition());
    if (filter == null) return Collections.emptySet();

    Set<LookupElement> variants = completeReference(qualifier, qualifier, filter, parameters, qualifierMatcher);

    // add type names available in context
    PsiShortNamesCache namesCache = PsiShortNamesCache.getInstance(qualifier.getProject());
    for (PsiClass aClass : namesCache.getClassesByName(referenceName, qualifier.getResolveScope())) {
      variants.add(JavaClassNameCompletionContributor.createClassLookupItem(aClass, true));
    }

    if (!variants.isEmpty()) return variants;

    // add import items (types)
    Set<LookupElement> allClasses = new LinkedHashSet<LookupElement>();
    CompletionParameters qualifierParameters = parameters.withPosition(qualifier.getReferenceNameElement(),
                                                                       qualifier.getTextRange().getEndOffset());
    JavaClassNameCompletionContributor.addAllClasses(qualifierParameters, true, qualifierMatcher,
                                                     new CollectConsumer<LookupElement>(allClasses));

    return allClasses;
  }

  @NotNull
  private static Set<LookupElement> completeReference(@NotNull PsiElement element,
                                                      PsiReference reference,
                                                      @NotNull ElementFilter filter,
                                                      @NotNull CompletionParameters parameters,
                                                      @NotNull PrefixMatcher matcher) {

    if (reference instanceof PsiMultiReference) {
      PsiReference[] references = ((PsiMultiReference)reference).getReferences();
      reference = ContainerUtil.findInstance(references, PsiJavaReference.class);
    }

    if (reference instanceof PsiJavaReference) {
      JavaCompletionProcessor.Options options = JavaCompletionProcessor.Options
        .DEFAULT_OPTIONS.withFilterStaticAfterInstance(parameters.getInvocationCount() <= 1);

      MyElementFilter elementFilter = new MyElementFilter(filter);
      return JavaCompletionUtil.processJavaReference(
        element, (PsiJavaReference)reference, elementFilter, options, matcher, parameters);
    }

    return Collections.emptySet();
  }

  static final class MyElementFilter implements ElementFilter {
    @NotNull private final ElementFilter myFilter;

    public MyElementFilter(@NotNull ElementFilter filter) {
      myFilter = filter;
    }

    @Override
    public boolean isAcceptable(Object element, PsiElement context) {
      return myFilter.isAcceptable(element, context);
    }

    @Override
    public boolean isClassAcceptable(Class hintClass) {
      return ReflectionCache.isAssignable(PsiClass.class, hintClass)
             || ReflectionCache.isAssignable(PsiVariable.class, hintClass)
             || ReflectionCache.isAssignable(PsiMethod.class, hintClass)
             || ReflectionCache.isAssignable(CandidateInfo.class, hintClass);
    }
  }
}
