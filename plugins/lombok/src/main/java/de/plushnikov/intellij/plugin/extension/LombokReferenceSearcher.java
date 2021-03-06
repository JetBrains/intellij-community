package de.plushnikov.intellij.plugin.extension;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import de.plushnikov.intellij.plugin.psi.LombokLightFieldBuilder;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Annas example: org.jetbrains.plugins.javaFX.fxml.refs.JavaFxControllerFieldSearcher
 * Alternative Implementation for LombokFieldFindUsagesHandlerFactory
 */
public class LombokReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  public LombokReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor consumer) {
    PsiElement refElement = queryParameters.getElementToSearch();

    if (refElement instanceof PsiField) {
      DumbService.getInstance(queryParameters.getProject()).runReadActionInSmartMode(() ->
        processPsiField((PsiField) refElement, queryParameters.getOptimizer()));
    }
  }

  private void processPsiField(final PsiField refPsiField, final SearchRequestCollector collector) {
    final PsiClass containingClass = refPsiField.getContainingClass();
    if (null != containingClass) {
      processClassMethods(refPsiField, collector, containingClass);

      final PsiClass[] innerClasses = containingClass.getInnerClasses();
      Arrays.stream(innerClasses)
        .forEach(psiClass -> processClassMethods(refPsiField, collector, psiClass));

      Arrays.stream(innerClasses)
        .forEach(psiClass -> processClassFields(refPsiField, collector, psiClass));
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

  private void processClassFields(PsiField refPsiField, SearchRequestCollector collector, PsiClass containingClass) {
    Arrays.stream(containingClass.getFields())
      .filter(LombokLightFieldBuilder.class::isInstance)
      .filter(psiField -> psiField.getNavigationElement() == refPsiField)
      .filter(psiField -> Objects.nonNull(psiField.getName()))
      .forEach(psiField -> {
        collector.searchWord(psiField.getName(), psiField.getUseScope(), UsageSearchContext.IN_CODE, true, psiField);
      });
  }

}
