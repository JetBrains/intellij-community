package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * User : ktisha
 */

public class PyChangeSignatureUsageViewDescriptor extends UsageViewDescriptorAdapter {
  protected PsiElement[] myDeclarationsElements;

  public PyChangeSignatureUsageViewDescriptor(UsageInfo[] usages) {
    final Collection<PsiElement> declarationsElements = new ArrayList<PsiElement>();
    for (UsageInfo info : usages) {
      declarationsElements.add(info.getElement());
    }
    myDeclarationsElements = PsiUtilCore.toPsiElementArray(declarationsElements);
  }

  @NotNull
  @Override
  public PsiElement[] getElements() {
    return myDeclarationsElements;
  }

  @Override
  public String getProcessedElementsHeader() {
    return PyBundle.message("refactoring.change.signature.usage.view.declarations.header");
  }
}
