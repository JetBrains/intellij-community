package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

/**
 * @author max
 */
public class ProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.ProblemDescriptorImpl");

  private SmartPsiElementPointer mySmartPointer;
  private final String myDescriptionTemplate;
  private final LocalQuickFix myFix;
  private ProblemHighlightType myHighlightType;

  public ProblemDescriptorImpl(PsiElement psiElement, String descriptionTemplate, LocalQuickFix fix, ProblemHighlightType highlightType) {
    LOG.assertTrue(psiElement.isValid());
    LOG.assertTrue(psiElement.isPhysical());
    myFix = fix;
    myHighlightType = highlightType;
    final Project project = psiElement.getProject();
    mySmartPointer = SmartPointerManager.getInstance(project).createLazyPointer(psiElement);
    myDescriptionTemplate = descriptionTemplate;
  }

  public PsiElement getPsiElement() {
    return mySmartPointer.getElement();
  }

  public int getLineNumber() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return -1;
    LOG.assertTrue(psiElement.isPhysical());
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiElement.getContainingFile());
    if (document == null) return -1;
    return document.getLineNumber(psiElement.getTextOffset()) + 1;
  }

  public LocalQuickFix getFix() {
    return myFix;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

}
