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

import java.util.*;

/**
 * @author cdr
 */
public class SliceUtil {
  public static boolean processUsagesFlownDownToTheExpression(PsiExpression expression, Processor<SliceUsage> processor, SliceUsage parent) {
    PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
    if (method == null) return true;
    expression = simplify(expression);
    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable)) return true;

      Collection<PsiExpression> expressions = getExpressionsFlownTo(expression, method, (PsiVariable)resolved);

      return processFlownFromExpressions(expressions, processor, parent, ref);
    }
    else if (expression instanceof PsiMethodCallExpression) {
      return processMethodReturnValue((PsiMethodCallExpression)expression, processor, parent);
    }
    return true;
  }

  private static PsiExpression simplify(final PsiExpression expression) {
    if (expression instanceof PsiParenthesizedExpression) {
      return simplify(((PsiParenthesizedExpression)expression).getExpression());
    }
    if (expression instanceof PsiTypeCastExpression) {
      return simplify(((PsiTypeCastExpression)expression).getOperand());
    }
    return expression;
  }

  private static Collection<PsiExpression> getExpressionsFlownTo(final PsiExpression expression, final PsiMethod containingMethod, final PsiVariable variable) {
    ValuableDataFlowRunner runner = new ValuableDataFlowRunner(expression);
    assert PsiTreeUtil.isAncestor(containingMethod, expression, true);
    if (runner.analyzeMethod(containingMethod.getBody()) != RunnerResult.OK) return Collections.emptyList();

    Collection<PsiExpression> expressions = runner.getPossibleVariableValues(variable);

    if (variable instanceof PsiField || variable instanceof PsiParameter) {
      expressions = new THashSet<PsiExpression>(expressions);
      expressions.add(expression);
    }
    return expressions;
  }

  private static boolean processFlownFromExpressions(final Collection<PsiExpression> expressions, final Processor<SliceUsage> processor,
                                                     final SliceUsage parent, final PsiReferenceExpression ref) {
    for (PsiExpression flowFromExpression : expressions) {
      if (flowFromExpression instanceof PsiReferenceExpression) {
        PsiElement element = ((PsiReferenceExpression)flowFromExpression).resolve();
        if (element instanceof PsiParameter) {
          if (!processParameterUsages((PsiParameter)element, processor, parent)) return false;
          continue;
        }
        if (element instanceof PsiField) {
          if (!processFieldUsages((PsiField)element, processor, parent)) return false;
          continue;
        }
      }
      //generic usage
      SliceUsage usage = new SliceUsage(new UsageInfo(flowFromExpression), parent);
      if (!processor.process(usage)) return false;
    }

    return true;
  }

  private static boolean processMethodReturnValue(final PsiMethodCallExpression methodCallExpr, final Processor<SliceUsage> processor,
                                                  final SliceUsage parent) {
    final PsiMethod methodCalled = methodCallExpr.resolveMethod();
    if (methodCalled == null) return true;

    PsiType returnType = methodCalled.getReturnType();
    if (returnType == null) return true;
    final PsiCodeBlock body = methodCalled.getBody();
    if (body == null) return true;

    final Collection<PsiExpression> expressions = new THashSet<PsiExpression>();
    body.accept(new JavaRecursiveElementVisitor() {
      public void visitReturnStatement(final PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (!(returnValue instanceof PsiReferenceExpression)) return;
        PsiElement resolved = ((PsiReferenceExpression)returnValue).resolve();
        if (!(resolved instanceof PsiVariable)) return;

        expressions.addAll(getExpressionsFlownTo(returnValue, methodCalled, (PsiVariable)resolved));
      }
    });

    return processFlownFromExpressions(expressions, processor, parent, methodCallExpr.getMethodExpression());
  }

  private static boolean processFieldUsages(final PsiField field, final Processor<SliceUsage> processor, final SliceUsage parent) {
    return ReferencesSearch.search(field).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        PsiElement element = reference.getElement();
        if (!(element instanceof PsiReferenceExpression)) return true;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        if (!PsiUtil.isOnAssignmentLeftHand(referenceExpression)) return true;
        PsiExpression rExpression = ((PsiAssignmentExpression)referenceExpression.getParent()).getRExpression();
        SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(rExpression), parent, field);
        return processor.process(usage);
      }
    });
  }

  private static boolean processParameterUsages(final PsiParameter parameter, final Processor<SliceUsage> processor, final SliceUsage parent) {
    PsiElement declarationScope = parameter.getDeclarationScope();
    if (!(declarationScope instanceof PsiMethod)) return true;
    PsiMethod method = (PsiMethod)declarationScope;

    final int paramSeqNo = ArrayUtil.find(method.getParameterList().getParameters(), parameter);
    assert paramSeqNo != -1;

    Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(method.findDeepestSuperMethods()));
    superMethods.add(method);
    final Set<PsiReference> processed = new THashSet<PsiReference>(); //usages of super method and overridden method can overlap
    for (final PsiMethod containingMethod : superMethods) {
      if (!MethodReferencesSearch.search(containingMethod).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          if (!processed.add(reference)) return true;
          PsiElement element = reference.getElement().getParent();
          if (!(element instanceof PsiCall)) return true;
          final PsiCall call = (PsiCall)element;
          PsiExpression passExpression = call.getArgumentList().getExpressions()[paramSeqNo];
          SliceParameterUsage usage = new SliceParameterUsage(new UsageInfo(passExpression), parameter, parent);
          return processor.process(usage);
        }
      })) return false;
    }

    return true;
  }
}
