/*
 * @author ven
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class AddSingleMemberStaticImportAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddSingleMemberStaticImportAction");
  private static final Key<PsiElement> TEMP_REFERENT_USER_DATA = new Key<PsiElement>("TEMP_REFERENT_USER_DATA");

  public String getFamilyName() {
    return "Add Single-Member Static Import";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!file.isWritable()) return false;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)element.getParent()).getQualifierExpression() != null) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
      PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiMember &&
          ((PsiModifierListOwner)resolved).hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass aClass = ((PsiMember)resolved).getContainingClass();
        if (aClass != null && !PsiTreeUtil.isAncestor(aClass, element, true)) {
          String qName = aClass.getQualifiedName();
          if (qName != null) {
            qName = qName + "." +refExpr.getReferenceName();
            if (file instanceof PsiJavaFile) {
              if (((PsiJavaFile)file).getImportList().findSingleImportStatement(refExpr.getReferenceName()) == null) {
                String text = MessageFormat.format("Add static import for ''{0}''", new Object[]{qName});
                setText(text);
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiElement resolved = refExpr.resolve();

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (!expression.isQualified() && refExpr.getReferenceName().equals(expression.getReferenceName())) {
          PsiElement resolved = expression.resolve();
          if (resolved != null) {
            expression.putUserData(TEMP_REFERENT_USER_DATA, resolved);
          }
        }

        if (expression.getQualifierExpression() != null) super.visitElement(expression.getQualifierExpression());
      }
    });

    PsiImportStaticStatement importStaticStatement = file.getManager().getElementFactory().createImportStaticStatement(((PsiMember)resolved).getContainingClass(),
                                                                                                                       ((PsiNamedElement)resolved).getName());
    ((PsiJavaFile)file).getImportList().addAfter(importStaticStatement, null);

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (refExpr.getReferenceName().equals(expression.getReferenceName())) {
          if (!expression.isQualified()) {
            PsiElement referent = expression.getUserData(TEMP_REFERENT_USER_DATA);

            if (referent instanceof PsiMember && referent != expression.resolve()) {
              PsiElementFactory factory = expression.getManager().getElementFactory();
              try {
                PsiReferenceExpression copy = (PsiReferenceExpression)factory.createExpressionFromText("A." + expression.getReferenceName(), null);
                expression = (PsiReferenceExpression)expression.replace(copy);
                ((PsiReferenceExpression)expression.getQualifierExpression()).bindToElement(((PsiMember)referent).getContainingClass());
              }
              catch (IncorrectOperationException e) {
                LOG.error (e);
              }
            }
            expression.putUserData(TEMP_REFERENT_USER_DATA, null);
          } else {
            if (expression.getQualifierExpression() instanceof PsiReferenceExpression) {
              PsiElement aClass = ((PsiReferenceExpression)expression.getQualifierExpression()).resolve();
              if (aClass == ((PsiMember)resolved).getContainingClass()) {
                try {
                  expression.getQualifierExpression().delete();
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                }
              }
            }
          }
        }

        if (expression.getQualifierExpression() != null) super.visitElement(expression.getQualifierExpression());
      }
    });
  }
}