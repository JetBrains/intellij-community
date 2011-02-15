package com.jetbrains.python.validation;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptorImpl;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Alexey.Ivanov
 */
public class UnsupportedFeatures extends CompatibilityVisitor {

  public UnsupportedFeatures() {
    super(new ArrayList<LanguageLevel>());
  }

  @Override
  public void visitPyElement(PyElement node) {
    setVersionsToProcess(Arrays.asList(getLanguageLevel(node)));
  }

  @Override
  protected void registerProblem(PsiElement node, String message, LocalQuickFix localQuickFix, boolean asError) {
    if (node == null || node.getTextLength() == 0){
          return;
    }
    if (localQuickFix != null)
      if (asError)
        getHolder().createErrorAnnotation(node, message).registerFix(createIntention(node, message, localQuickFix));
      else
        getHolder().createWarningAnnotation(node, message).registerFix(createIntention(node, message, localQuickFix));
    else
      if (asError)
        getHolder().createErrorAnnotation(node, message);
      else
        getHolder().createWarningAnnotation(node, message);
  }

  @NotNull
  private static LanguageLevel getLanguageLevel(PyElement node) {
    VirtualFile virtualFile = node.getContainingFile().getVirtualFile();
    if (virtualFile != null) {
      return LanguageLevel.forFile(virtualFile);
    }
    return LanguageLevel.getDefault();
  }

  private static IntentionAction createIntention(PsiElement node, String message, LocalQuickFix fix) {
    LocalQuickFix[] quickFixes = {fix};
    CommonProblemDescriptorImpl descr = new ProblemDescriptorImpl(node, node, message,
                                                                  quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
                                                                  node.getTextRange(), true);
    return QuickFixWrapper.wrap((ProblemDescriptor)descr, 0);
  }
}
