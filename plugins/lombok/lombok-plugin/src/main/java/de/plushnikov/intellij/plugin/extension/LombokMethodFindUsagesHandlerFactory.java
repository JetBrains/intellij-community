package de.plushnikov.intellij.plugin.extension;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import de.plushnikov.intellij.plugin.util.PsiAnnotationUtil;
import lombok.experimental.Delegate;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class LombokMethodFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  public LombokMethodFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PsiMethod;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, this) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        final PsiMethod method = (PsiMethod) element;
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null) {
          final Query<PsiReference> query = ReferencesSearch.search(containingClass);
          final Collection<PsiReference> references = query.findAll();
          for (PsiReference reference : references) {
            final PsiElement psiElement = reference.resolve();
            if (psiElement instanceof PsiField) {
              if (PsiAnnotationUtil.isAnnotatedWith((PsiField) psiElement, Delegate.class)) {
                return new PsiElement[]{psiElement};
              }
            }
          }
        }
        return super.getSecondaryElements();
      }
    };
  }
}
