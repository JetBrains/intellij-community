package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author ven
 */
public class AddOnDemandStaticImportAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AddOnDemandStaticImportAction");

  public String getFamilyName() {
    return "Add On Demand Static Import";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!file.isWritable()) return false;
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
      PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
        PsiElement resolved = refExpr.resolve();
      if (resolved instanceof PsiClass) {
        String text = MessageFormat.format("Add on demand static import for ''{0}''", new Object[]{((PsiClass)resolved).getQualifiedName()});
        setText(text);
        return true;
      }
    }

    return false;
  }

  public void invoke(final Project project, final Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)element.getParent();
    final PsiClass aClass = (PsiClass)refExpr.resolve();
    PsiImportStaticStatement importStaticStatement = file.getManager().getElementFactory().createImportStaticStatement(aClass, "*");
    ((PsiJavaFile)file).getImportList().addAfter(importStaticStatement, null);

    file.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifierExpression).isReferenceTo(aClass)) {
          try {
            PsiReferenceExpression copy = (PsiReferenceExpression)expression.copy();
            PsiElement resolved = copy.resolve();
            copy.getQualifierExpression().delete();
            PsiManager manager = expression.getManager();
            if (manager.areElementsEquivalent(copy.resolve(), resolved)) {
              qualifierExpression.delete();
              HighlightManager.getInstance(project).addRangeHighlight(editor, expression.getTextRange().getStartOffset(),
                                                                      expression.getTextRange().getEndOffset(),
                                                                      EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES),
                                                                      false, null);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        if (qualifierExpression != null) super.visitElement(qualifierExpression);
      }
    });
  }
}
