package com.intellij.slicer;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiParameter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;
import com.intellij.analysis.AnalysisScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter {
  private final SliceUsage myParent;
  private final AnalysisScope myScope;

  public SliceUsage(@NotNull UsageInfo usageInfo, @NotNull SliceUsage parent) {
    super(usageInfo);
    myParent = parent;
    myScope = parent.myScope;
    assert myScope != null;
  }
  public SliceUsage(@NotNull UsageInfo usageInfo, @NotNull AnalysisScope scope) {
    super(usageInfo);
    myParent = null;
    myScope = scope;
  }

  public void processChildren(Processor<SliceUsage> processor) {
    PsiElement element = getElement();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    //indicator.setText2("<html><body>Searching for usages of "+ StringUtil.trimStart(SliceManager.getElementDescription(element),"<html><body>")+"</body></html>");
    indicator.checkCanceled();
    if (element instanceof PsiExpression) {
      SliceUtil.processUsagesFlownDownToTheExpression((PsiExpression)element, processor, this);
    }
    else if (element instanceof PsiField) {
      SliceUtil.processFieldUsages((PsiField)element, processor, this);
    }
    else if (element instanceof PsiParameter) {
      SliceUtil.processParameterUsages((PsiParameter)element, processor, this);
    }
  }

  public SliceUsage getParent() {
    return myParent;
  }

  @NotNull
  public AnalysisScope getScope() {
    return myScope;
  }

  SliceUsage copy() {
    return getParent() == null ? new SliceUsage(getUsageInfo(), getScope()) : new SliceUsage(getUsageInfo(), getParent());
  }
}
