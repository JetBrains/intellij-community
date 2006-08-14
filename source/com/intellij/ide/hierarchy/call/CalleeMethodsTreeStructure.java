package com.intellij.ide.hierarchy.call;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor;
import com.intellij.ide.hierarchy.HierarchyTreeStructure;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public final class CalleeMethodsTreeStructure extends HierarchyTreeStructure {
  public static final String TYPE = IdeBundle.message("title.hierarchy.callees.of");
  private final String myScopeType;

  /**
   * Should be called in read action
   */
  public CalleeMethodsTreeStructure(final Project project, final PsiMethod method, final String scopeType) {
    super(project, new CallHierarchyNodeDescriptor(project, null, method, true, false));
    myScopeType = scopeType;
  }

  protected final Object[] buildChildren(final HierarchyNodeDescriptor descriptor) {
    final PsiMember enclosingElement = ((CallHierarchyNodeDescriptor)descriptor).getEnclosingElement();
    if (!(enclosingElement instanceof PsiMethod)) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final PsiMethod method = (PsiMethod)enclosingElement;

    final ArrayList<PsiMethod> methods = new ArrayList<PsiMethod>();

    final PsiCodeBlock body = method.getBody();
    if (body != null) {
      visitor(body, methods);
    }

    final PsiMethod baseMethod = (PsiMethod)((CallHierarchyNodeDescriptor)getBaseDescriptor()).getTargetElement();
    final PsiClass baseClass = baseMethod.getContainingClass();

    final HashMap<PsiMethod,CallHierarchyNodeDescriptor> methodToDescriptorMap = new HashMap<PsiMethod, CallHierarchyNodeDescriptor>();

    final ArrayList<CallHierarchyNodeDescriptor> result = new ArrayList<CallHierarchyNodeDescriptor>();

    for (final PsiMethod calledMethod : methods) {
      if (CallHierarchyBrowser.SCOPE_CLASS.equals(myScopeType)) {
        if (!PsiTreeUtil.isAncestor(baseClass, calledMethod, true)) {
          continue;
        }
      }
      else if (CallHierarchyBrowser.SCOPE_PROJECT.equals(myScopeType)) {
        if (!calledMethod.getManager().isInProject(calledMethod)) {
          continue;
        }
      }

      CallHierarchyNodeDescriptor d = methodToDescriptorMap.get(calledMethod);
      if (d == null) {
        d = new CallHierarchyNodeDescriptor(myProject, descriptor, calledMethod, false, false);
        methodToDescriptorMap.put(calledMethod, d);
        result.add(d);
      }
      else {
        d.incrementUsageCount();
      }
    }

    // also add overriding methods as children
    final PsiSearchHelper searchHelper = method.getManager().getSearchHelper();
    final PsiMethod[] overridingMethods = searchHelper.findOverridingMethods(method, method.getUseScope(), true);
    for (final PsiMethod overridingMethod : overridingMethods) {
      final CallHierarchyNodeDescriptor node = new CallHierarchyNodeDescriptor(myProject, descriptor, overridingMethod, false, false);
      if (!result.contains(node)) result.add(node);
    }

/*
    // show method implementations in EJB Class
    final PsiMethod[] ejbImplementations = EjbUtil.findEjbImplementations(method, null);
    for (int i = 0; i < ejbImplementations.length; i++) {
      PsiMethod ejbImplementation = ejbImplementations[i];
      result.add(new CallHierarchyNodeDescriptor(myProject, descriptor, ejbImplementation, false));
    }
*/
    return result.toArray(new Object[result.size()]);
  }


  private void visitor(final PsiElement element, final ArrayList<PsiMethod> methods) {
    final PsiElement[] children = element.getChildren();
    for (final PsiElement child : children) {
      visitor(child, methods);
      if (child instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)child;
        final PsiReferenceExpression methodExpression = callExpression.getMethodExpression();
        final PsiMethod method = (PsiMethod)methodExpression.resolve();
        if (method != null) {
          methods.add(method);
        }
      }
      else if (child instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)child;
        final PsiMethod method = newExpression.resolveConstructor();
        if (method != null) {
          methods.add(method);
        }
      }
    }
  }
}
