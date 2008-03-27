package com.intellij.slicer;

import com.intellij.codeInspection.dataFlow.RunnerResult;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;

import java.util.*;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter {
  final PsiMethod myTargetMethod;
  final PsiParameter myTargetParameter;
  final PsiParameter myContainingParameter;
  final PsiMethod myContainingMethod;
  final SliceUsage myUseSite;
  boolean duplicate;
  final Map<SliceUsage, List<SliceUsage>> targetEqualUsages;

  protected SliceUsage(UsageInfo usageInfo, PsiMethod targetMethod, PsiParameter targetParameter, PsiParameter containingParameter,
                       SliceUsage useSite, Map<SliceUsage, List<SliceUsage>> usages) {
    super(usageInfo);
    myTargetMethod = targetMethod;
    myTargetParameter = targetParameter;
    myContainingParameter = containingParameter;
    targetEqualUsages = usages;
    myContainingMethod = PsiTreeUtil.getParentOfType(myContainingParameter, PsiMethod.class);
    myUseSite = useSite;
  }

  public void initializeDuplicateFlag() {
    List<SliceUsage> eq = targetEqualUsages.get(this);
    if (eq == null) {
      eq = new SmartList<SliceUsage>();
      targetEqualUsages.put(this, eq);
    }
    eq.add(this);
    if (eq.size() != 1) {
      /*for (SliceUsage usage : eq) {
        usage.*/duplicate = true;
      /*}*/
    }
  }

  private SliceUsage(PsiReference reference, PsiMethod targetMethod, PsiParameter targetParameter, PsiParameter containingParameter,
                     SliceUsage useSite, Map<SliceUsage, List<SliceUsage>> usages) {
    this(new UsageInfo(reference), targetMethod, targetParameter, containingParameter, useSite, usages);
  }

  public String presentableText() {
    return getPresentation().getPlainText();
  }

  public void processChildren(final Processor<SliceUsage> processor) {
    if (myContainingMethod == null) return;
    final int paramSeqNo = ArrayUtil.find(myContainingMethod.getParameterList().getParameters(), myContainingParameter);
    assert paramSeqNo != -1;

    Collection<PsiMethod> superMethods = new THashSet<PsiMethod>(Arrays.asList(myContainingMethod.findDeepestSuperMethods()));
    superMethods.add(myContainingMethod);
    for (final PsiMethod method : superMethods) {
      final PsiParameter containingParameter = method.getParameterList().getParameters()[paramSeqNo];
      MethodReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          PsiElement element = reference.getElement().getParent();
          if (element instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression expression = (PsiMethodCallExpression)element;

            PsiExpression passExpression = expression.getArgumentList().getExpressions()[paramSeqNo];
            Collection<PsiParameter> parameters = getMethodParametersFlownDownToTheExpression(expression, passExpression);
            for (PsiParameter parameter : parameters.isEmpty() ? Collections.<PsiParameter>singletonList(null) : parameters) {
              SliceUsage usage = new SliceUsage(reference, method, containingParameter, parameter, SliceUsage.this, targetEqualUsages);
              if (!processor.process(usage)) return false;
            }
          }
          return true;
        }
      });
    }
  }

  public static Collection<PsiParameter> getMethodParametersFlownDownToTheExpression(PsiMethodCallExpression methodCallExpression,
                                                                                     PsiExpression argument) {
    PsiMethod method = PsiTreeUtil.getParentOfType(argument, PsiMethod.class);
    if (method == null) return Collections.emptyList();
    if (!(argument instanceof PsiReferenceExpression)) return Collections.emptyList();
    PsiReferenceExpression ref = (PsiReferenceExpression)argument;
    PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiVariable)) return Collections.emptyList();

    MyDataFlowRunner runner = new MyDataFlowRunner(methodCallExpression, (PsiReferenceExpression)argument);
    assert PsiTreeUtil.isAncestor(method, argument, true);
    if (runner.analyzeMethod(method.getBody()) != RunnerResult.OK) return Collections.emptyList();

    return runner.getFlownFromParameters();
  }
}
