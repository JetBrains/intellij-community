package com.intellij.slicer;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.TextChunk;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class SliceUsage extends UsageInfo2UsageAdapter {
  private final SliceUsage myParent;

  public SliceUsage(@NotNull UsageInfo usageInfo, SliceUsage parent) {
    super(usageInfo);
    myParent = parent;
  }

  public void processChildren(Processor<SliceUsage> processor) {
    PsiElement element = getElement();
    if (!(element instanceof PsiExpression)) return;
    SliceUtil.processUsagesFlownDownToTheExpression((PsiExpression)element, processor, this);
  }

  public void customizeTreeCellRenderer(ColoredTreeCellRenderer treeCellRenderer) {
    TextChunk[] text = getPresentation().getText();
    for (TextChunk textChunk : text) {
      treeCellRenderer.append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
    }
  }
}
