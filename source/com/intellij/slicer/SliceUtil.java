package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * @author cdr
 */
public class SliceUtil {
  public static boolean processUsagesFlownDownToTheExpression(PsiExpression expression, Processor<SliceUsage> processor, SliceUsage parent) {
    expression = simplify(expression);
    Processor<SliceUsage> uniqueProcessor =
        new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new TObjectHashingStrategy<SliceUsage>() {
          public int computeHashCode(final SliceUsage object) {
            return object.getUsageInfo().hashCode();
          }

          public boolean equals(final SliceUsage o1, final SliceUsage o2) {
            return o1.getUsageInfo().equals(o2.getUsageInfo());
          }
        });

    if (expression instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)expression;
      PsiElement resolved = ref.resolve();
      if (!(resolved instanceof PsiVariable)) return true;
      final PsiVariable variable = (PsiVariable)resolved;
      final Set<PsiExpression> expressions = new THashSet<PsiExpression>(DfaUtil.getCachedVariableValues(variable, ref));
      if (variable.hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) expressions.add(initializer);
      }
      if (!expressions.isEmpty()) {
        if (!processFlownFromExpressions(expressions, uniqueProcessor, parent)) return false;
      }
      if (resolved instanceof PsiField) {
        return processFieldUsages((PsiField)resolved, processor, parent);
      }
      else if (resolved instanceof PsiParameter) {
        return processParameterUsages((PsiParameter)resolved, processor, parent);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      return processMethodReturnValue((PsiMethodCallExpression)expression, uniqueProcessor, parent);
    }
    if (expression instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)expression;
      PsiExpression thenE = conditional.getThenExpression();
      PsiExpression elseE = conditional.getElseExpression();
      if (thenE != null && !processUsagesFlownDownToTheExpression(thenE, processor, parent)) return false;
      if (elseE != null && !processUsagesFlownDownToTheExpression(elseE, processor, parent)) return false;
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

  private static boolean processFlownFromExpressions(final Collection<PsiExpression> expressions, final Processor<SliceUsage> processor,
                                                     final SliceUsage parent) {
    for (PsiExpression exp : expressions) {
      final PsiExpression realExpression;
      realExpression = exp.getParent() instanceof DummyHolder? (PsiExpression) ((DummyHolder)exp.getParent()).getContext() : exp;
      assert realExpression != null;
      SliceUsage usage = new SliceUsage(new UsageInfo(realExpression), parent);
      if (!processor.process(usage)) return false;
    }

    return true;
  }

  private static boolean processMethodReturnValue(final PsiMethodCallExpression methodCallExpr, final Processor<SliceUsage> processor, final SliceUsage parent) {
    final PsiMethod methodCalled = methodCallExpr.resolveMethod();
    if (methodCalled == null) return true;

    PsiType returnType = methodCalled.getReturnType();
    if (returnType == null) return true;
    final PsiCodeBlock body = methodCalled.getBody();
    if (body == null) return true;

    final boolean[] result = {true};
    body.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitAnonymousClass(PsiAnonymousClass aClass) {
        // do not look for returns there
      }

      public void visitReturnStatement(final PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) return;
        UsageInfo usageInfo = new UsageInfo(returnValue);
        SliceUsage usage = new SliceUsage(usageInfo, parent);
        if (!processor.process(usage)) {
          stopWalking();
          result[0] = false;
        }
      }
    });

    return result[0];
  }

  static boolean processFieldUsages(final PsiField field, final Processor<SliceUsage> processor, final SliceUsage parent) {
    if (field.hasInitializer()) {
      PsiExpression initializer = field.getInitializer();
      if (initializer != null) {
        SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(initializer), parent, field);
        if (!processor.process(usage)) return false;
      }
    }
    return ReferencesSearch.search(field).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference reference) {
        SliceManager.getInstance(field.getProject()).checkCanceled();
        PsiElement element = reference.getElement();
        if (!(element instanceof PsiReferenceExpression)) return true;
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        PsiElement parentExpr = referenceExpression.getParent();
        if (PsiUtil.isOnAssignmentLeftHand(referenceExpression)) {
          PsiExpression rExpression = ((PsiAssignmentExpression)parentExpr).getRExpression();
          SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(rExpression), parent, field);
          return processor.process(usage);
        }
        if (parentExpr instanceof PsiPrefixExpression && ((PsiPrefixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPrefixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(parentExpr), parent, field);
          return processor.process(usage);
        }
        if (parentExpr instanceof PsiPostfixExpression && ((PsiPostfixExpression)parentExpr).getOperand() == referenceExpression && ( ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.PLUSPLUS || ((PsiPostfixExpression)parentExpr).getOperationTokenType() == JavaTokenType.MINUSMINUS)) {
          SliceFieldUsage usage = new SliceFieldUsage(new UsageInfo(parentExpr), parent, field);
          return processor.process(usage);
        }
        return true;
      }
    });
  }

  static boolean processParameterUsages(final PsiParameter parameter, final Processor<SliceUsage> processor, final SliceUsage parent) {
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
          synchronized (processed) {
            if (!processed.add(reference)) return true;
          }
          PsiElement element = reference.getElement().getParent();
          PsiExpressionList argumentList;
          if (element instanceof PsiAnonymousClass) {
            argumentList = ((PsiAnonymousClass)element).getArgumentList();
          }
          else {
            if (!(element instanceof PsiCall)) return true;
            argumentList = ((PsiCall)element).getArgumentList();
          }
          PsiExpression passExpression = argumentList.getExpressions()[paramSeqNo];
          SliceParameterUsage usage = new SliceParameterUsage(new UsageInfo(passExpression), parameter, parent);
          return processor.process(usage);
        }
      })) return false;
    }

    return true;
  }
}
