package com.intellij.slicer;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiTreeUtil;
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

  public void customizeTreeCellRenderer(ColoredTreeCellRenderer treeCellRenderer) {
    TextChunk[] text = getPresentation().getText();
    for (TextChunk textChunk : text) {
      treeCellRenderer.append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
    }

    PsiElement element = getElement();
    PsiMethod method;
    PsiClass aClass;
    while (true) {
      method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      aClass = method == null ? PsiTreeUtil.getParentOfType(element, PsiClass.class) : method.getContainingClass();
      if (aClass instanceof PsiAnonymousClass) {
        element = aClass;
      }
      else {
        break;
      }
    }
    String location = method != null ? PsiFormatUtil.formatMethod(method, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS | PsiFormatUtil.SHOW_CONTAINING_CLASS, PsiFormatUtil.SHOW_TYPE, 2)
                    : aClass != null ? PsiFormatUtil.formatClass(aClass, PsiFormatUtil.SHOW_NAME)
                    : null;
    if (location != null) {
      treeCellRenderer.append(" in " + location, SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
