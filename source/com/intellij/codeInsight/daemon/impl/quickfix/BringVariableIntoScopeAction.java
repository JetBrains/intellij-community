package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

/**
 * @author ven
 */
public class BringVariableIntoScopeAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeAction");
  PsiReferenceExpression myUnresolvedReference;
  PsiLocalVariable myOutOfScopeVariable;

  public BringVariableIntoScopeAction(PsiReferenceExpression unresolvedReference) {
    myUnresolvedReference = unresolvedReference;
  }

  public String getText() {
    LOG.assertTrue(myOutOfScopeVariable != null);
    String varText = PsiFormatUtil.formatVariable(myOutOfScopeVariable, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE, PsiSubstitutor.EMPTY);
    return MessageFormat.format("Bring ''{0}'' into Scope", new Object[] {varText});
  }

  public String getFamilyName() {
    return "Bring Variable to Scope";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    final String referenceName = myUnresolvedReference.getReferenceName();
    if (referenceName == null) return false;

    PsiManager manager = file.getManager();
    if (!myUnresolvedReference.isValid() || !manager.isInProject(myUnresolvedReference)) return false;

    PsiElement container = PsiTreeUtil.getParentOfType(myUnresolvedReference, new Class[] {PsiCodeBlock.class, PsiClass.class});
    if (!(container instanceof PsiCodeBlock)) return false;

    myOutOfScopeVariable = null;
    while(container.getParent() instanceof PsiStatement || container.getParent() instanceof PsiCatchSection) container = container.getParent();
    container.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {}

      public void visitExpression(PsiExpression expression) {
        //Don't look inside expressions
      }

      public void visitLocalVariable(PsiLocalVariable variable) {
        if (referenceName.equals(variable.getName())) {
          if (myOutOfScopeVariable == null) myOutOfScopeVariable = variable;
          else                              myOutOfScopeVariable = null; //2 conflict variables
        }
      }
    });

    return myOutOfScopeVariable != null;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    LOG.assertTrue(myOutOfScopeVariable != null);
    PsiManager manager = file.getManager();
    if (PsiUtil.isAccessedForWriting(myUnresolvedReference)) {
      //Leave initializer assignment
      PsiExpression initializer = myOutOfScopeVariable.getInitializer();
      if (initializer != null) {
        PsiExpressionStatement assignment = (PsiExpressionStatement)manager.getElementFactory().createStatementFromText(myOutOfScopeVariable.getName() + "= e;", null);
        ((PsiAssignmentExpression)assignment.getExpression()).getRExpression().replace(initializer);
        assignment = (PsiExpressionStatement)manager.getCodeStyleManager().reformat(assignment);
        PsiDeclarationStatement declStatement = PsiTreeUtil.getParentOfType(myOutOfScopeVariable, PsiDeclarationStatement.class);
        LOG.assertTrue(declStatement != null);
        declStatement.getParent().addAfter(assignment, declStatement);
        myOutOfScopeVariable.getInitializer().delete();
      }
    }

    myOutOfScopeVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
    PsiElement commonParent = PsiTreeUtil.findCommonParent(myOutOfScopeVariable, myUnresolvedReference);
    LOG.assertTrue(commonParent != null);
    PsiElement child = myOutOfScopeVariable.getTextRange().getStartOffset() < myUnresolvedReference.getTextRange().getStartOffset() ?
                       ((PsiElement)myOutOfScopeVariable) : myUnresolvedReference;

    while(child.getParent() != commonParent) child = child.getParent();
    PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)manager.getElementFactory().createStatementFromText("int i = 0", null);
    newDeclaration.getDeclaredElements()[0].replace(myOutOfScopeVariable);

    while(!(child instanceof PsiStatement) || !(child.getParent() instanceof PsiCodeBlock)) {
      child = child.getParent();
      commonParent = commonParent.getParent();
    }
    LOG.assertTrue(commonParent != null);
    commonParent.addBefore(newDeclaration, child);

    myOutOfScopeVariable.delete();
    manager.getCodeStyleManager().reformat(commonParent);
    QuickFixAction.spoilDocument(project, file);
    DaemonCodeAnalyzer.getInstance(project).updateVisibleHighlighters(editor);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
