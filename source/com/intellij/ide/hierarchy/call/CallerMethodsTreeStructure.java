package com.intellij.ide.hierarchy.call;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
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

    SearchScope searchScope = GlobalSearchScope.allScope(myProject);
    if (CallHierarchyBrowser.SCOPE_CLASS.equals(myScopeType)) {
      final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
      final PsiClass containingClass = baseMethod.getContainingClass();
      searchScope = new LocalSearchScope(containingClass);
    }
    else if (CallHierarchyBrowser.SCOPE_PROJECT.equals(myScopeType)) {
      searchScope = GlobalSearchScope.projectProductionScope(myProject);
    }
    else if (CallHierarchyBrowser.SCOPE_TEST.equals(myScopeType)) {
      searchScope = GlobalSearchScope.projectTestScope(myProject);
    }

    final Set<PsiMethod> methodsToFind = new HashSet<PsiMethod>();
    methodsToFind.add(method);
    methodsToFind.addAll(Arrays.asList(method.findDeepestSuperMethods()));

    final Map<PsiMember,CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<PsiMember, CallHierarchyNodeDescriptor>();
    for (final PsiMethod methodToFind : methodsToFind) {
      MethodReferencesSearch.search(methodToFind, searchScope, true).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          if (reference instanceof PsiReferenceExpression) {
            final PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
            if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
              final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
              final PsiClass methodClass = method.getContainingClass();
              if (methodClass != null && methodClass.isInheritor(superClass, true)) {
                return true;
              }
            }
          }
          else {
            if (!(reference instanceof PsiElement)) {
              return true;
            }

            final PsiElement parent = ((PsiElement)reference).getParent();
            if (parent instanceof PsiNewExpression) {
              if (((PsiNewExpression)parent).getClassReference() != reference) {
                return true;
              }
            }
            else if (parent instanceof PsiAnonymousClass) {
              if (((PsiAnonymousClass)parent).getBaseClassReference() != reference) {
                return true;
              }
            }
            else {
              return true;
            }
          }

          final PsiElement element = reference.getElement();
          final PsiMember key = CallHierarchyNodeDescriptor.getEnclosingElement(element);

          synchronized (methodToDescriptorMap) {
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
          return true;
        }
      });
    }

    return methodToDescriptorMap.values().toArray(new Object[methodToDescriptorMap.size()]);
  }

  @Override
  public boolean isAlwaysShowPlus() {
    return true;
  }
}
