package com.intellij.slicer.forward;

import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.slicer.SliceUsage;
import com.intellij.slicer.SliceManager;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * @author cdr
 */
public class SliceFUtil {
  public static boolean processUsagesFlownFromThe(@NotNull PsiElement element, @NotNull Processor<SliceUsage> processor, @NotNull SliceUsage parent) {
    PsiElement target = getAssignmentTarget(element);
    if (target != null) {
      SliceUsage usage = new SliceUsage(new UsageInfo(target), parent);
      return processor.process(usage);
    }

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)element;
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable)) return true;
      final PsiVariable variable = (PsiVariable)resolved;
      return processAssignedFrom(variable, ref, parent, processor);
    }
    if (element instanceof PsiVariable) {
      return processAssignedFrom(element, element, parent, processor);
    }
    if (element instanceof PsiMethod) {
      return processAssignedFrom(element, element, parent, processor);
    }
    return true;
  }

  private static boolean processAssignedFrom(final PsiElement from, final PsiElement context, final SliceUsage parent,
                                                 final Processor<SliceUsage> processor) {
    if (from instanceof PsiLocalVariable) {
      return ReferencesSearch.search(from).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference reference) {
          PsiElement element = reference.getElement();
          if (element.getTextOffset() < context.getTextOffset()) return true;

          return processAssignmentTarget(element, parent, processor);
        }
      });
    }
    if (from instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)from;
      PsiElement scope = parameter.getDeclarationScope();
      Collection<PsiParameter> parametersToAnalyze = new THashSet<PsiParameter>();
      if (scope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)scope;
        int index = method.getParameterList().getParameterIndex(parameter);

        Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
        superMethods.add(method);
        for (Iterator<PsiMethod> iterator = superMethods.iterator(); iterator.hasNext(); ) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          PsiMethod superMethod = iterator.next();
          if (superMethod instanceof PsiCompiledElement) {
            iterator.remove();
          }
        }

        final THashSet<PsiMethod> implementors = new THashSet<PsiMethod>(superMethods);
        for (PsiMethod superMethod : superMethods) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          if (!OverridingMethodsSearch.search(superMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiMethod>() {
            public boolean process(PsiMethod sub) {
              SliceManager.getInstance(method.getProject()).checkCanceled();
              implementors.add(sub);
              return true;
            }
          })) return false;
        }
        for (PsiMethod implementor : implementors) {
          SliceManager.getInstance(method.getProject()).checkCanceled();
          PsiParameter[] parameters = implementor.getParameterList().getParameters();
          if (index != -1 && index < parameters.length) {
            parametersToAnalyze.add(parameters[index]);
          }
        }
      }
      else {
        parametersToAnalyze.add(parameter);
      }
      for (final PsiParameter psiParameter : parametersToAnalyze) {
        SliceManager.getInstance(from.getProject()).checkCanceled();

        if (!ReferencesSearch.search(psiParameter).forEach(new Processor<PsiReference>() {
          public boolean process(PsiReference reference) {
            SliceManager.getInstance(from.getProject()).checkCanceled();
            PsiElement element = reference.getElement();

            return processAssignmentTarget(element, parent, processor);
          }
        })) return false;
      }
      return true;
    }
    if (from instanceof PsiField) {
      return ReferencesSearch.search(from).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference reference) {
          SliceManager.getInstance(from.getProject()).checkCanceled();
          PsiElement element = reference.getElement();
          return processAssignmentTarget(element, parent, processor);
        }
      });
    }

    if (from instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)from;

      Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
      superMethods.add(method);
      final Set<PsiReference> processed = new THashSet<PsiReference>(); //usages of super method and overridden method can overlap
      for (final PsiMethod containingMethod : superMethods) {
        if (!MethodReferencesSearch.search(containingMethod, parent.getScope().toSearchScope(), true).forEach(new Processor<PsiReference>() {
            public boolean process(final PsiReference reference) {
              SliceManager.getInstance(from.getProject()).checkCanceled();
              synchronized (processed) {
                if (!processed.add(reference)) return true;
              }
              PsiElement element = reference.getElement().getParent();
              if (element instanceof PsiCompiledElement) return true;

              return processAssignmentTarget(element, parent, processor);
            }
          })) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean processAssignmentTarget(PsiElement element, SliceUsage pa, Processor<SliceUsage> processor) {
    PsiElement target = getAssignmentTarget(element);
    if (target != null) {
      SliceUsage usage = new SliceUsage(new UsageInfo(element), pa);
      return processor.process(usage);
    }
    return true;
  }

  private static PsiElement getAssignmentTarget(PsiElement element) {
    element = complexify(element);
    PsiElement target = null;
    //assignment
    PsiElement parent = element.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (element.equals(assignment.getRExpression())) {
        PsiElement left = assignment.getLExpression();
        if (left instanceof PsiReferenceExpression) {
          target = ((PsiReferenceExpression)left).resolve();
        }
      }
    }
    else if (parent instanceof PsiVariable) {
      PsiVariable variable = (PsiVariable)parent;

      PsiElement initializer = variable.getInitializer();
      if (element.equals(initializer)) {
        target = variable;
      }
    }
    //method call
    else if (parent instanceof PsiExpressionList && parent.getParent() instanceof PsiCallExpression) {
      PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
      int index = ArrayUtil.find(expressions, element);
      PsiCallExpression methodCall = (PsiCallExpression)parent.getParent();
      PsiMethod method = methodCall.resolveMethod();
      if (index != -1 && method != null) {
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (index < parameters.length) {
          target = parameters[index];
        }
      }
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiReturnStatement statement = (PsiReturnStatement)parent;
      if (element.equals(statement.getReturnValue())) {
        target = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
      }
    }
    if (target instanceof PsiCompiledElement) {
      target = null;
    }
    return target;
  }

  private static PsiElement complexify(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiParenthesizedExpression && element.equals(((PsiParenthesizedExpression)parent).getExpression())) {
      return complexify(parent);
    }
    if (parent instanceof PsiTypeCastExpression && element.equals(((PsiTypeCastExpression)parent).getOperand())) {
      return complexify(parent);
    }
    return element;
  }
}
