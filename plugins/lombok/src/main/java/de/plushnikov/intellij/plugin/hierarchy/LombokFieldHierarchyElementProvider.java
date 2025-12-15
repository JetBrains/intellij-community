package de.plushnikov.intellij.plugin.hierarchy;

import com.intellij.ide.hierarchy.call.CallHierarchyElementProvider;
import com.intellij.ide.hierarchy.call.CallHierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.call.JavaCallHierarchyData;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import de.plushnikov.intellij.plugin.psi.LombokLightMethodBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LombokFieldHierarchyElementProvider implements CallHierarchyElementProvider {

  @Override
  public boolean canProvide(@NotNull PsiMember element) {
    return element instanceof PsiField;
  }

  @Override
  public Collection<PsiElement> provideReferencedMembers(@NotNull PsiMember elementToSearch) {
    final PsiClass containingClass = elementToSearch.getContainingClass();
    if (containingClass != null) {
      final Collection<PsiElement> result = new ArrayList<>();

      Arrays.stream(containingClass.getMethods())
        .filter(LombokLightMethodBuilder.class::isInstance)
        .map(LombokLightMethodBuilder.class::cast)
        .filter(psiMethod -> psiMethod.getNavigationElement() == elementToSearch || psiMethod.hasRelatedMember(elementToSearch))
        .forEach(result::add);

      Arrays.stream(containingClass.getInnerClasses())
        .map(PsiClass::getMethods)
        .flatMap(Arrays::stream)
        .filter(LombokLightMethodBuilder.class::isInstance)
        .map(LombokLightMethodBuilder.class::cast)
        .filter(psiMethod -> psiMethod.hasRelatedMember(elementToSearch))
        .forEach(result::add);

      return result;
    }
    return Collections.emptyList();
  }

  @Override
  public void appendReferencedMethods(@NotNull PsiMethod methodToFind, @NotNull JavaCallHierarchyData hierarchyData) {
    if (methodToFind.isConstructor()) {
      final PsiClass containingClass = methodToFind.getContainingClass();
      if (null != containingClass) {
        Arrays.stream(containingClass.getInnerClasses())
          .map(PsiClass::getMethods)
          .flatMap(Arrays::stream)
          .filter(LombokLightMethodBuilder.class::isInstance)
          .map(LombokLightMethodBuilder.class::cast)
          .filter(methodBuilder -> methodBuilder.hasRelatedMember(methodToFind))
          .forEach(methodBuilder -> {
            CallHierarchyNodeDescriptor parentDescriptor = (CallHierarchyNodeDescriptor)hierarchyData.getNodeDescriptor();
            final Map<PsiMember, NodeDescriptor<?>> nodeDescriptorMap = hierarchyData.getResultMap();
            CallHierarchyNodeDescriptor d = (CallHierarchyNodeDescriptor)nodeDescriptorMap.get(methodBuilder);
            if (d == null) {
              d = new CallHierarchyNodeDescriptor(hierarchyData.getProject(), parentDescriptor, methodBuilder, false, true);
              nodeDescriptorMap.put(methodBuilder, d);
            }
          });
      }
    }
  }
}