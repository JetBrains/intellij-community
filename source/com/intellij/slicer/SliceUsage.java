package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.TextChunk;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter {
  protected final SliceUsage myParent;
  boolean duplicate;
  protected final Map<SliceUsage, List<SliceUsage>> targetEqualUsages;

  public SliceUsage(@NotNull UsageInfo usageInfo, @NotNull Map<SliceUsage, List<SliceUsage>> usages, SliceUsage parent) {
    super(usageInfo);
    targetEqualUsages = usages;
    myParent = parent;
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

  public void processChildren(Processor<SliceUsage> processor) {
    PsiElement element = getElement();
    if (!(element instanceof PsiExpression)) return;
    SliceUtil.processUsagesFlownDownToTheExpression((PsiExpression)element, processor, this, targetEqualUsages);
  }

  public void customizeTreeCellRenderer(ColoredTreeCellRenderer treeCellRenderer) {
    TextChunk[] text = getPresentation().getText();
    for (TextChunk textChunk : text) {
      treeCellRenderer.append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
    }
  }
}
