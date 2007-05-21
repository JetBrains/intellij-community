/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 06.05.2002
 * Time: 14:38:40
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.introduceParameter;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Util {

  public static void analyzeExpression(PsiExpression expr,
                                       List<UsageInfo> localVars,
                                       List<UsageInfo> classMemberRefs,
                                       List<UsageInfo> params) {

    if (expr instanceof PsiThisExpression || expr instanceof PsiSuperExpression) {
      classMemberRefs.add(new ClassMemberInExprUsageInfo(expr));
    }
    else if (expr instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)expr;

      PsiElement subj = refExpr.resolve();

      if (subj instanceof PsiParameter) {
        params.add(new ParameterInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiLocalVariable) {
        localVars.add(new LocalVariableInExprUsageInfo(refExpr));
      }
      else if (subj instanceof PsiField || subj instanceof PsiMethod) {
        classMemberRefs.add(new ClassMemberInExprUsageInfo(refExpr));
      }

    }

    PsiElement[] children = expr.getChildren();

    for (PsiElement child : children) {
      if (child instanceof PsiExpression) {
        analyzeExpression((PsiExpression)child, localVars, classMemberRefs, params);
      }
    }
  }

  public static PsiMethod getContainingMethod(PsiElement expr) {
    return PsiTreeUtil.getParentOfType(expr, PsiMethod.class);
  }

  public static boolean anyFieldsWithGettersPresent(List<UsageInfo> classMemberRefs) {
    for (UsageInfo usageInfo : classMemberRefs) {

      if (usageInfo.getElement() instanceof PsiReferenceExpression) {
        PsiElement e = ((PsiReferenceExpression)usageInfo.getElement()).resolve();

        if (e instanceof PsiField) {
          PsiField psiField = (PsiField)e;
          PsiMethod getterPrototype = PropertyUtil.generateGetterPrototype(psiField);

          PsiMethod getter = psiField.getContainingClass().findMethodBySignature(getterPrototype, true);

          if (getter != null) return true;
        }
      }
    }

    return false;
  }

  // returns parameters that are used solely in specified expression
  @NotNull
  public static TIntArrayList findParametersToRemove(@NotNull PsiMethod method, @NotNull final PsiExpression expr) {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 0) return new TIntArrayList();

    PsiSearchHelper helper = method.getManager().getSearchHelper();
    PsiMethod[] overridingMethods = helper.findOverridingMethods(method, method.getUseScope(), true);
    final PsiMethod[] allMethods = ArrayUtil.append(overridingMethods, method);

    final TIntHashSet suspects = new TIntHashSet();
    expr.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiParameter) {
          int i = ArrayUtil.find(parameters, resolved);
          if (i != -1) {
            suspects.add(i);
          }
        }
      }
    });

    final TIntIterator iterator = suspects.iterator();
    while(iterator.hasNext()) {
      final int paramNum = iterator.next();
      for (PsiMethod psiMethod : allMethods) {
        PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
        if (paramNum >= psiParameters.length) continue;
        PsiParameter parameter = psiParameters[paramNum];
        if (!ReferencesSearch.search(parameter, parameter.getResolveScope(), false).forEach(new Processor<PsiReference>() {
          public boolean process(final PsiReference reference) {
            PsiElement element = reference.getElement();
            boolean stillCanBeRemoved = element != null && (PsiTreeUtil.isAncestor(expr, element, false) || PsiUtil.isInsideJavadocComment(element));
            if (!stillCanBeRemoved) {
              iterator.remove();
              return false;
            }
           return true;
          }
        })) break;
      }
    }

    return new TIntArrayList(suspects.toArray());
  }
}
