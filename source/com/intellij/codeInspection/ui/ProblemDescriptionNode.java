package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;

import javax.swing.*;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  public static final Icon INFO = IconLoader.getIcon("/compiler/information.png");
  private RefElement myElement;
  private ProblemDescriptor myDescriptor;

  public ProblemDescriptionNode(RefElement element, ProblemDescriptor descriptor) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
  }

  public RefElement getElement() { return myElement; }
  public ProblemDescriptor getDescriptor() { return myDescriptor; }

  public Icon getIcon(boolean expanded) {
    return INFO;
  }

  public int getProblemCount() {
    return 1;
  }

  public boolean isValid() {
    if (!myElement.isValid()) return false;
    final PsiElement psiElement = myDescriptor.getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  public String toString() {
    return isValid() ? renderDescriptionMessage(myDescriptor) : "";
  }

  private static String renderDescriptionMessage(ProblemDescriptor descriptor) {
    PsiElement psiElement = descriptor.getPsiElement();
    String message = descriptor.getDescriptionTemplate();

    if (psiElement != null && psiElement.isValid() && message != null) {
      message = message.replaceAll("<[^>]*>", "");
      message = StringUtil.replace(message, "#ref", psiElement.getText());
      message = StringUtil.replace(message, "#loc", "");
      return message;
    }
    return "";
  }
}
