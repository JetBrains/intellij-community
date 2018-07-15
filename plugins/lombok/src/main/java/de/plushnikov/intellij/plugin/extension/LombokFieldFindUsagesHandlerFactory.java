package de.plushnikov.intellij.plugin.extension;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.util.PsiUtilCore;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * It should find calls to getters/setters of some filed changed by lombok accessors
 */
public class LombokFieldFindUsagesHandlerFactory extends FindUsagesHandlerFactory {

  public LombokFieldFindUsagesHandlerFactory() {
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    if (element instanceof PsiField && !DumbService.isDumb(element.getProject())) {
      final PsiField psiField = (PsiField) element;
      final PsiClass containingClass = psiField.getContainingClass();
      if (containingClass != null) {
        return Arrays.stream(containingClass.getAllMethods())
          .anyMatch(LombokLightMethodBuilder.class::isInstance);
      }
    }
    return false;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new FindUsagesHandler(element) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        final PsiField psiField = (PsiField) getPsiElement();
        final PsiClass containingClass = psiField.getContainingClass();
        if (containingClass != null) {

          final Collection<PsiElement> elements = new ArrayList<>();
          processClassMethods(containingClass, psiField, elements);

          Arrays.stream(containingClass.getInnerClasses())
            .forEach(psiClass -> processClassMethods(psiClass, psiField, elements));

          return PsiUtilCore.toPsiElementArray(elements);
        }
        return PsiElement.EMPTY_ARRAY;
      }

      private void processClassMethods(PsiClass containingClass, PsiField refPsiField, Collection<PsiElement> collector) {
        Arrays.stream(containingClass.getMethods())
          .filter(LombokLightMethodBuilder.class::isInstance)
          .filter(psiMethod -> psiMethod.getNavigationElement() == refPsiField)
          .forEach(collector::add);
      }
    };
  }
}
