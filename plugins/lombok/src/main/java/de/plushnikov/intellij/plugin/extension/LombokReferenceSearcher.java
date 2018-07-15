package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class LombokReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public LombokReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    PsiElement refElement = queryParameters.getElementToSearch();

    if (refElement instanceof PsiField) {
      processPsiField((PsiField) refElement, queryParameters.getOptimizer());
    }
  }

  private void processPsiField(final PsiField refPsiField, final SearchRequestCollector collector) {
    final PsiClass containingClass = refPsiField.getContainingClass();
    if (null != containingClass) {
      processClassMethods(refPsiField, collector, containingClass);

      Arrays.stream(containingClass.getInnerClasses())
        .forEach(psiClass -> processClassMethods(refPsiField, collector, psiClass));
    }
  }

  private void processClassMethods(PsiField refPsiField, SearchRequestCollector collector, PsiClass containingClass) {
    Arrays.stream(containingClass.getMethods())
      .filter(LombokLightMethodBuilder.class::isInstance)
      .filter(psiMethod -> psiMethod.getNavigationElement() == refPsiField)
      .forEach(psiMethod -> {
        collector.searchWord(psiMethod.getName(), psiMethod.getUseScope(), UsageSearchContext.IN_CODE, true, psiMethod);
      });
  }

}
