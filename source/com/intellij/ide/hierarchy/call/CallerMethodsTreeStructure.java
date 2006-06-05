package com.intellij.ide.hierarchy.call;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class CallerMethodsTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = IdeBundle.message("title.hierarchy.callers.of");
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CallerMethodsTreeStructure(final Project project, final PsiMethod method, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
    myScopeType = scopeType;
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (!(enclosingElement instanceof PsiMethod)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final PsiMethod method = (PsiMethod)enclosingElement;

    final PsiSearchHelper searchHelper = method.getManager().getSearchHelper();

    SearchScope searchScope = GlobalSearchScope.projectScope(myProject);
    if (CallHierarchyBrowser.SCOPE_CLASS.equals(myScopeType)) {
      final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
      final PsiClass containingClass = baseMethod.getContainingClass();
      searchScope = new LocalSearchScope(containingClass);
    }
    else if (CallHierarchyBrowser.SCOPE_ALL.equals(myScopeType)) {
      searchScope = GlobalSearchScope.allScope(myProject);
    }

    final Set<PsiMethod> methodsToFind = new HashSet<PsiMethod>();
    methodsToFind.add(method);
    methodsToFind.addAll(Arrays.asList(method.findDeepestSuperMethods()));

    final HashMap<PsiMember,CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<PsiMember, CallHierarchyNodeDescriptor>();
    for (final PsiMethod methodToFind : methodsToFind) {
      for (final PsiReference reference : searchHelper.findReferencesIncludingOverriding(methodToFind, searchScope, true)) {
        if (reference instanceof PsiReferenceExpression) {
          final PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
          if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
            final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass != null && methodClass.isInheritor(superClass, true)) {
              continue;
            }
          }
        }
        else {
          if (!(reference instanceof PsiElement)) {
            continue;
          }

          final PsiElement parent = ((PsiElement)reference).getParent();
          if (parent instanceof PsiNewExpression) {
            if (((PsiNewExpression)parent).getClassReference() != reference) {
              continue;
            }
          }
          else if (parent instanceof PsiAnonymousClass) {
            if (((PsiAnonymousClass)parent).getBaseClassReference() != reference) {
              continue;
            }
          }
          else {
            continue;
          }
        }

        final PsiElement element = reference.getElement();
        final PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

        CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(key);
        if (d == null) {
          d = new CallHierarchyNodeDescriptor(myProject, descriptor, element, false, true);
          methodToDescriptorMap.put(key, d);
        }
        else {
          d.incrementUsageCount();
        }
        d.addReference(reference);
      }
    }

    return methodToDescriptorMap.values().toArray(new Object[methodToDescriptorMap.size()]);
  }
}
