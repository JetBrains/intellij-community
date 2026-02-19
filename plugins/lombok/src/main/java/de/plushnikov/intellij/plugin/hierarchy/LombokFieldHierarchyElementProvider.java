package de.plushnikov.intellij.plugin.hierarchy;

import com.intellij.ide.hierarchy.call.CallHierarchyElementProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
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
  public @NotNull Collection<PsiElement> provideReferencedMembers(@NotNull PsiMember psiMember) {
    // only PsiFields are supported now
    if(!(psiMember instanceof PsiField)) {
      return Collections.emptyList();
    }

    // skip early if no lombok in current module
    final Module module = ModuleUtilCore.findModuleForPsiElement(psiMember);
    if (!LombokLibraryUtil.hasLombokClasses(module)) {
      return Collections.emptyList();
    }

    final PsiClass containingClass = psiMember.getContainingClass();
    if (containingClass != null) {
      final Collection<PsiElement> result = new ArrayList<>();

      Arrays.stream(containingClass.getMethods())
        .filter(LombokLightMethodBuilder.class::isInstance)
        .map(LombokLightMethodBuilder.class::cast)
        .filter(psiMethod -> psiMethod.getNavigationElement() == psiMember || psiMethod.hasRelatedMember(psiMember))
        .forEach(result::add);

      Arrays.stream(containingClass.getInnerClasses())
        .map(PsiClass::getMethods)
        .flatMap(Arrays::stream)
        .filter(LombokLightMethodBuilder.class::isInstance)
        .map(LombokLightMethodBuilder.class::cast)
        .filter(psiMethod -> psiMethod.hasRelatedMember(psiMember))
        .forEach(result::add);

      return result;
    }
    return Collections.emptyList();
  }

  @Override
  public @NotNull List<PsiMethod> provideReferencedMethods(@NotNull PsiMethod methodToFind) {
    // skip early if no lombok in current module
    final Module module = ModuleUtilCore.findModuleForPsiElement(methodToFind);
    if (!LombokLibraryUtil.hasLombokClasses(module)) {
      return Collections.emptyList();
    }

    if (methodToFind.isConstructor()) {
      final PsiClass containingClass = methodToFind.getContainingClass();
      if (null != containingClass) {
        return Arrays.stream(containingClass.getInnerClasses())
          .map(PsiClass::getMethods)
          .flatMap(Arrays::stream)
          .filter(LombokLightMethodBuilder.class::isInstance)
          .map(LombokLightMethodBuilder.class::cast)
          .filter(methodBuilder -> methodBuilder.hasRelatedMember(methodToFind))
          .collect(Collectors.toList());
      }
    }
    return Collections.emptyList();
  }
}