package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class AddVariableInitializerFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiVariable myVariable;

  public AddVariableInitializerFix(PsiVariable variable) {
    myVariable = variable;
  }

  public String getText() {
    return "Initialize variable '"+myVariable.getName()+"'";
  }

  public String getFamilyName() {
    return "Initialize variable";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myVariable != null
        && myVariable.isValid()
        && myVariable.getManager().isInProject(myVariable)
        && !myVariable.hasInitializer()
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myVariable.getContainingFile())) return;

    try {
      String initializerText = suggestInitializer();
      PsiElementFactory factory = myVariable.getManager().getElementFactory();
      PsiExpression initializer = factory.createExpressionFromText(initializerText, myVariable);
      if (myVariable instanceof PsiLocalVariable) {
        ((PsiLocalVariable)myVariable).setInitializer(initializer);
      }
      else if (myVariable instanceof PsiField) {
        ((PsiField)myVariable).setInitializer(initializer);
      }
      else {
        LOG.error("Unknown variable type: "+myVariable);
      }
      TextRange range = myVariable.getInitializer().getTextRange();
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private String suggestInitializer() {
    PsiType type = myVariable.getType();
    return CodeInsightUtil.getDefaultValueOfType(type);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
