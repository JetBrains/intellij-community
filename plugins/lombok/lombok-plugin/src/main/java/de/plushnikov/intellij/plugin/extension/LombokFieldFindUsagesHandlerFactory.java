package de.plushnikov.intellij.plugin.extension;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;

public class LombokFieldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  public LombokFieldFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PsiField;
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, this) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        PsiElement element = getPsiElement();
        final PsiField field = (PsiField) element;
        PsiClass containingClass = field.getContainingClass();
//        if (containingClass != null) {
//          return PsiElement.EMPTY_ARRAY;
//        }
        return super.getSecondaryElements();
      }
    };
  }
}
