package com.intellij.slicer;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.slicer.forward.SliceFUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import gnu.trove.TObjectHashingStrategy;
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

  public void processChildren(Processor<SliceUsage> processor, boolean dataFlowToThis) {
    PsiElement element = getElement();
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    //indicator.setText2("<html><body>Searching for usages of "+ StringUtil.trimStart(SliceManager.getElementDescription(element),"<html><body>")+"</body></html>");
    indicator.checkCanceled();

    Processor<SliceUsage> uniqueProcessor =
      new CommonProcessors.UniqueProcessor<SliceUsage>(processor, new TObjectHashingStrategy<SliceUsage>() {
        public int computeHashCode(final SliceUsage object) {
          return object.getUsageInfo().hashCode();
        }

        public boolean equals(final SliceUsage o1, final SliceUsage o2) {
          return o1.getUsageInfo().equals(o2.getUsageInfo());
        }
      });

    if (dataFlowToThis) {
      SliceUtil.processUsagesFlownDownTo(element, uniqueProcessor, this);
    }
    else {
      SliceFUtil.processUsagesFlownFromThe(element, uniqueProcessor, this);
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
