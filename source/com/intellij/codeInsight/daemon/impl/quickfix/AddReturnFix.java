package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AddReturnFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.AddReturnFix");
  private final PsiMethod myMethod;

  public AddReturnFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return "Add Return Statement";
  }

  public String getFamilyName() {
    return "Add Return Statement";
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod != null
        && myMethod.isValid()
        && myMethod.getManager().isInProject(myMethod)
        && myMethod.getBody() != null
        && myMethod.getBody().getRBrace() != null
        ;
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(myMethod.getContainingFile())) return;

    try {
      final String value = suggestReturnValue();
      final PsiElementFactory factory = myMethod.getManager().getElementFactory();
      PsiReturnStatement returnStatement = (PsiReturnStatement) factory.createStatementFromText("return " + value+";", myMethod);
      final PsiCodeBlock body = myMethod.getBody();
      returnStatement = (PsiReturnStatement) body.addBefore(returnStatement, body.getRBrace());

      TextRange range = returnStatement.getReturnValue().getTextRange();
      int offset = range.getStartOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
      editor.getSelectionModel().setSelection(range.getEndOffset(), range.getStartOffset());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private String suggestReturnValue() {
    final PsiType type = myMethod.getReturnType();
    // first try to find suitable local variable
    PsiVariable[] variables = getDeclaredVariables(myMethod);
    for (int i = 0; i < variables.length; i++) {
      PsiVariable variable = variables[i];
      if (variable.getType() != null
          && type.equals(variable.getType())) return variable.getName();
    }
    return CodeInsightUtil.getDefaultValueOfType(type);
  }

  private PsiVariable[] getDeclaredVariables(PsiMethod method) {
    List variables = new ArrayList();
    final PsiStatement[] statements = method.getBody().getStatements();
    for (int i = 0; i < statements.length; i++) {
      PsiStatement statement = statements[i];
      if (statement instanceof PsiDeclarationStatement) {
        final PsiElement[] declaredElements = ((PsiDeclarationStatement) statement).getDeclaredElements();
        for (int j = 0; j < declaredElements.length; j++) {
          PsiElement declaredElement = declaredElements[j];
          if (declaredElement instanceof PsiLocalVariable) variables.add(declaredElement);
        }
      }
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    variables.addAll(Arrays.asList(parameters));
    return (PsiVariable[]) variables.toArray(new PsiVariable[variables.size()]);
  }

  public boolean startInWriteAction() {
    return true;
  }

}
