package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;

/**
 * @author max
 */
public class LocalQuickFixWrapper extends QuickFixAction {
  private DescriptorProviderInspection myTool;

  public LocalQuickFixWrapper(DescriptorProviderInspection tool) {
    super("Apply fix", tool);
    myTool = tool;
  }

  public void update(AnActionEvent e) {
    super.update(e);
    if (e.getPresentation().isEnabled()) {
      final InspectionResultsView invoker = getInvoker(e);
      if (invoker != null) {
        ProblemDescriptor[] descriptors = invoker.getSelectedDescriptors();
        boolean hasFixes = false;
        for (int i = 0; i < descriptors.length; i++) {
          ProblemDescriptor descriptor = descriptors[i];
          if (descriptor.getFix() != null && descriptor.getPsiElement() != null) {
            hasFixes = true;
            break;
          }
        }

        if (hasFixes) {
          e.getPresentation().setVisible(true);
          e.getPresentation().setEnabled(true);
          e.getPresentation().setText(getName(descriptors));
          return;
        }
      }
    }

    e.getPresentation().setVisible(false);
    e.getPresentation().setEnabled(false);
  }

  public String getText(RefElement where) {
    return getName(getDescriptors(where));
  }

  private ProblemDescriptor[] getDescriptors(RefElement refElement) {
    return myTool.getDescriptions(refElement);
  }

  private String getName(ProblemDescriptor[] descriptors) {
    String name = null;
    for (int i = 0; i < descriptors.length; i++) {
      ProblemDescriptor descriptor = descriptors[i];
      final LocalQuickFix fix = descriptor.getFix();
      if (fix != null) {
        if (name == null) {
          name = fix.getName();
        }
        else {
          if (!name.equals(fix.getName())) {
            name = "Apply fix";
          }
        }
      }
    }
    return name;
  }

  protected boolean applyFix(RefElement[] refElements) {
    for (int i = 0; i < refElements.length; i++) {
      RefElement refElement = refElements[i];
      ProblemDescriptor[] problems = myTool.getDescriptions(refElement);
      if (problems != null) {
        PsiElement psiElement = refElement.getElement();
        if (psiElement != null) {
          for (int j = 0; j < problems.length; j++) {
            LocalQuickFix fix = problems[j].getFix();
            if (fix != null) {
              fix.applyFix(psiElement.getProject(), problems[j]);
              myTool.ignoreProblem(refElement, problems[j]);
            }
          }
        }
      }
    }

    return true;
  }

  protected boolean isProblemDescriptorsAcceptable() {
    return true;
  }
}
