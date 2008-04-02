package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.codeInspection.dataFlow.ValuableDataFlowRunner;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import gnu.trove.THashSet;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceUtil {
  public static boolean processUsagesFlownDownToTheExpression(PsiExpression expression, Processor<SliceUsage> processor, SliceUsage parent,
                                                              final Map<SliceUsage, List<SliceUsage>> targetEqualUsages) {
    PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return true;
    if (!(expression instanceof PsiReferenceExpression)) return true;
    PsiReferenceExpression ref = (PsiReferenceExpression)expression;
    PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiVariable)) return true;

    ValuableDataFlowRunner runner = new ValuableDataFlowRunner(expression);
    assert PsiTreeUtil.isAncestor(method, expression, true);
    if (runner.analyzeMethod(method.getBody()) != RunnerResult.OK) return true;

    Collection<PsiExpression> expressions = runner.getPossibleVariableValues((PsiVariable)resolved);
    if (resolved instanceof PsiField || resolved instanceof PsiParameter) {
      expressions = new THashSet<PsiExpression>(expressions);
      expressions.add(expression);
    }

    for (PsiExpression flowFromExpression : expressions) {
      if (flowFromExpression instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)flowFromExpression).resolve();
        if (element instanceof PsiParameter) {
          if (!processParameterUsages((PsiParameter)element, processor, parent, targetEqualUsages)) return false;
          continue;
        }
        if (element instanceof PsiField) {
          if (!processFieldUsages((PsiField)element, processor, parent, targetEqualUsages)) return false;
          continue;
        }
      }
      //generic usage
      SliceUsage usage = new SliceUsage(new UsageInfo(ref), targetEqualUsages, parent);
      if (!processor.process(usage)) return false;
    }
    
    return true;
  }

  private static boolean processFieldUsages(final PsiField field, final Processor<SliceUsage> processor, final SliceUsage parent, final Map<SliceUsage, List<SliceUsage>> targetEqualUsages) {
    return ReferencesSearch.search(field).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        PsiElement element = reference.getElement();
        if (!(element instanceof PsiReferenceExpression)) return true;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        if (!PsiUtil.isOnAssignmentLeftHand(referenceExpression)) return true;
        PsiExpression rExpression = ((PsiAssignmentExpression)referenceExpression.getParent()).getRExpression();
        SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(rExpression), targetEqualUsages, parent, field);
        return processor.process(usage);
      }
    });
  }

  private static boolean processParameterUsages(final PsiParameter parameter, final Processor<SliceUsage> processor, final SliceUsage parent,
                                                final Map<SliceUsage, List<SliceUsage>> targetEqualUsages) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiMethod)) return true;
    PsiMethod method = (PsiMethod)declarationScope;

    final int paramSeqNo = ArrayUtil.find(method.getParameterList().getParameters(), parameter);
    assert paramSeqNo != -1;

    Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
    superMethods.add(method);
    for (final PsiMethod containingMethod : superMethods) {
      if (!MethodReferencesSearch.search(containingMethod).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          PsiElement element = reference.getElement().getParent();
          if (!(element instanceof PsiCall)) return true;
          final PsiCall call = (PsiCall)element;
          PsiExpression passExpression = call.getArgumentList().getExpressions()[paramSeqNo];
          SliceParameterUsage usage = new SliceParameterUsage(new UsageInfo(passExpression), parameter, parent, targetEqualUsages);
          return processor.process(usage);
        }
      })) return false;
    }

    return true;
  }
}
