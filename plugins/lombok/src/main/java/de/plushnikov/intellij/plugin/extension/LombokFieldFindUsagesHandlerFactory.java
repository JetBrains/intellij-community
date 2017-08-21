package de.plushnikov.intellij.plugin.extension;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import de.plushnikov.intellij.plugin.processor.field.AccessorsInfo;
import de.plushnikov.intellij.plugin.thirdparty.LombokUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class LombokFieldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory {
  public LombokFieldFindUsagesHandlerFactory(Project project) {
    super(project);
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element instanceof PsiField && !DumbService.isDumb(element.getProject());
  }

  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return new JavaFindUsagesHandler(element, this) {
      @NotNull
      @Override
      public PsiElement[] getSecondaryElements() {
        final PsiField psiField = (PsiField) getPsiElement();
        final PsiClass containingClass = psiField.getContainingClass();
        if (containingClass != null) {
          final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
          final String psiFieldName = psiField.getName();

          final String fieldName = accessorsInfo.removePrefix(psiFieldName);
          if (!fieldName.equals(psiFieldName)) {
            final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());

            final String getterName = LombokUtils.toGetterName(accessorsInfo, psiFieldName, isBoolean);
            final String setterName = LombokUtils.toSetterName(accessorsInfo, psiFieldName, isBoolean);

            final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
            final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);

            final Set<PsiElement> elements = new THashSet<PsiElement>(psiGetterMethods.length + psiSetterMethods.length);
            for (PsiMethod accessor : psiGetterMethods) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, ACTION_STRING));
            }
            for (PsiMethod accessor : psiSetterMethods) {
              ContainerUtil.addAll(elements, SuperMethodWarningUtil.checkSuperMethods(accessor, ACTION_STRING));
            }
            return PsiUtilCore.toPsiElementArray(elements);
          }
        }
        return super.getSecondaryElements();
      }
    };
  }
}
