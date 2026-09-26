package de.plushnikov.intellij.plugin.hierarchy;

import com.intellij.ide.hierarchy.call.CallHierarchyElementProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import de.plushnikov.intellij.plugin.util.LombokLibraryUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LombokFieldHierarchyElementProvider implements CallHierarchyElementProvider {

  @Override
  public @NotNull Collection<PsiMember> provideReferencedMembers(@NotNull PsiMember psiMember) {
    // only for PsiFields and PsiMethods now
    if (!(psiMember instanceof PsiField) && !(psiMember instanceof PsiMethod psiMethod && psiMethod.isConstructor())) {
      return Collections.emptyList();
    }

    // skip early if no lombok in the current module
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiMember);
    if (!LombokLibraryUtil.hasLombokClasses(module)) {
      return Collections.emptyList();
    }

    final PsiClass containingClass = psiMember.getContainingClass();
    if (containingClass != null) {
      if (psiMember instanceof PsiField) {
        return findRelatedMethodsForPsiField(psiMember, containingClass);
      }
      else {
        return findRelatedMethodsForPsiMethod(psiMember, containingClass);
      }
    }
    return Collections.emptyList();
  }

  private static @NotNull Collection<PsiMember> findRelatedMethodsForPsiField(@NotNull PsiMember psiMember,
                                                                              @NotNull PsiClass containingClass) {
    final Collection<PsiMember> result = new ArrayList<>();

    Arrays.stream(containingClass.getMethods()).filter(LombokLightMethodBuilder.class::isInstance).map(LombokLightMethodBuilder.class::cast)
      .filter(psiMethod -> psiMethod.getNavigationElement() == psiMember || psiMethod.hasRelatedMember(psiMember))
      .forEach(result::add);

    Arrays.stream(containingClass.getInnerClasses()).map(PsiClass::getMethods).flatMap(Arrays::stream)
      .filter(LombokLightMethodBuilder.class::isInstance).map(LombokLightMethodBuilder.class::cast)
      .filter(psiMethod -> psiMethod.hasRelatedMember(psiMember)).forEach(result::add);

    return result;
  }

  private static @NotNull List<PsiMember> findRelatedMethodsForPsiMethod(@NotNull PsiMember psiMember, @NotNull PsiClass containingClass) {
    return Arrays.stream(containingClass.getInnerClasses()).map(PsiClass::getMethods).flatMap(Arrays::stream)
      .filter(LombokLightMethodBuilder.class::isInstance).map(LombokLightMethodBuilder.class::cast)
      .filter(methodBuilder -> methodBuilder.hasRelatedMember(psiMember)).collect(Collectors.toList());
  }
}