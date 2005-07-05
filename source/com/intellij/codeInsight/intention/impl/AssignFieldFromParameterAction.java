package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;

public class AssignFieldFromParameterAction extends BaseIntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction");
  private PsiParameter myParameter;

  private  PsiType getType() {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    return type;
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    myParameter = CreateFieldFromParameterAction.findParameterAtCursor(file, editor);
    final PsiType type = getType();
    PsiClass targetClass = myParameter == null ? null : PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    if (myParameter == null
        || !myParameter.isValid()
        || !(myParameter.getDeclarationScope() instanceof PsiMethod)
        || !myParameter.getManager().isInProject(myParameter)
        || type == null
        || !type.isValid()
        || targetClass == null
        || targetClass.isInterface()
        || CreateFieldFromParameterAction.isParameterAssignedToField(myParameter)) {
      return false;
    }
    PsiField field = findFieldToAssign();
    if (field == null) return false;
    setText(MessageFormat.format("Assign Parameter to Field ''{0}''", new Object[]{field.getName(),}));

    return true;
  }

  public String getFamilyName() {
    return "Assign Parameter to Field";
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    myParameter = CreateFieldFromParameterAction.findParameterAtCursor(file, editor);
    if (!CodeInsightUtil.prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    try {
      PsiManager psiManager = PsiManager.getInstance(project);
      PsiElementFactory factory = psiManager.getElementFactory();
      PsiField field = findFieldToAssign();
      String fieldName = field.getName();
      String parameterName = myParameter.getName();
      final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();
      final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      PsiClass targetClass = method.getContainingClass();

      String stmtText = fieldName + " = " + parameterName + ";";
      if (fieldName.equals(parameterName)) {
        String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
        stmtText = prefix + stmtText;
      }

      PsiCodeBlock methodBody = method.getBody();
      PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
      assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(project).reformat(assignmentStmt);
      PsiStatement[] statements = methodBody.getStatements();
      int i;
      for (i = 0; i < statements.length; i++) {
        PsiStatement psiStatement = statements[i];

        if (psiStatement instanceof PsiExpressionStatement) {
          PsiExpressionStatement expressionStatement = (PsiExpressionStatement)psiStatement;
          PsiExpression expression = expressionStatement.getExpression();

          if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
            String text = methodCallExpression.getMethodExpression().getText();

            if (text.equals("super") || text.equals("this")) {
              continue;
            }
          }
        }
        break;
      }
      PsiElement inserted;
      if (i == statements.length) {
        inserted = methodBody.add(assignmentStmt);
      }
      else {
        inserted = methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
      }
      editor.getCaretModel().moveToOffset(inserted.getTextRange().getEndOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
  private PsiField findFieldToAssign() {
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(myParameter.getProject());
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, getType());

    final String fieldName = suggestedNameInfo.names[0];

    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    PsiField field = aClass.findFieldByName(fieldName, false);
    if (field == null) return null;
    if (!field.hasModifierProperty(PsiModifier.STATIC) && isMethodStatic) return null;

    return field;
  }


}
