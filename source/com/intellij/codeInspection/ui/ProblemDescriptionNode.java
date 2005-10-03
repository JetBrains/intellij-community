package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.xml.util.XmlUtil;

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

/**
 * @author max
 */
public class ProblemDescriptionNode extends InspectionTreeNode {
  public static final Icon INFO = IconLoader.getIcon("/compiler/information.png");
  private RefElement myElement;
  private ProblemDescriptor myDescriptor;
  private boolean myReplaceProblemDescriptorTemplateMessage;

  public ProblemDescriptionNode(RefElement element, ProblemDescriptor descriptor, boolean isReplaceProblemDescriptorTemplateMessage) {
    super(descriptor);
    myElement = element;
    myDescriptor = descriptor;
    myReplaceProblemDescriptorTemplateMessage = isReplaceProblemDescriptorTemplateMessage;
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
    return isValid() ? renderDescriptionMessage(myDescriptor, myReplaceProblemDescriptorTemplateMessage) : "";
  }

  private static String renderDescriptionMessage(ProblemDescriptor descriptor, boolean isReplaceProblemDescriptorTemplateMessage) {
    PsiElement psiElement = descriptor.getPsiElement();
    @NonNls String message = descriptor.getDescriptionTemplate();

    if (psiElement != null && psiElement.isValid() && message != null) {
      message = message.replaceAll("<[^>]*>", "");
      if (isReplaceProblemDescriptorTemplateMessage){
        message = StringUtil.replace(message, "#ref", psiElement.getText());
      } else {
        final int endIndex = message.indexOf("#end");
        if (endIndex > 0){
          message = message.substring(0, endIndex);
        }
      }
      message = StringUtil.replace(message, "#loc", "");
      message = XmlUtil.unescape(message);
      return message;
    }
    return "";
  }
}
