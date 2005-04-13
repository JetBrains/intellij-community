package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateFieldFromParameterAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.CreateFieldFromParameterAction");
  private PsiParameter myParameter;
  private final boolean forcedParameter;

  public CreateFieldFromParameterAction() {
    myParameter = null;
    forcedParameter = false;
  }
  public CreateFieldFromParameterAction(PsiParameter parameter) {
    myParameter = parameter;
    forcedParameter = true;
  }

  private  PsiType getType() {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    return type;
  }

  public String getText() {
    return MessageFormat.format("Create Field For Parameter ''{0}''", new Object[]{myParameter.getName(), });
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    myParameter = forcedParameter ? myParameter : findParameterAtCursor(file, editor);
    final PsiType type = getType();
    PsiClass targetClass = myParameter == null ? null : PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    return
      myParameter != null
      && myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && myParameter.getManager().isInProject(myParameter)
      && type != null
      && type.isValid()
      && !isParameterAssignedToField(myParameter)
      && targetClass != null
      && !targetClass.isInterface()
      ;
  }

  private static boolean isParameterAssignedToField(final PsiParameter parameter) {
    final PsiSearchHelper searchHelper = parameter.getManager().getSearchHelper();
    final PsiReference[] references = searchHelper.findReferences(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false);
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      if (!(reference instanceof PsiReferenceExpression)) continue;
      final PsiReferenceExpression expression = (PsiReferenceExpression)reference;
      if (!(expression.getParent() instanceof PsiAssignmentExpression)) continue;
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression.getParent();
      if (assignmentExpression.getRExpression() != expression) continue;
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (!(lExpression instanceof PsiReferenceExpression)) continue;
      final PsiElement element = ((PsiReferenceExpression)lExpression).resolve();
      if (!(element instanceof PsiField)) continue;
      return true;
    }
    return false;
  }

  private static PsiParameter findParameterAtCursor(final PsiFile file, final Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      return parameter;
    }
    if (!(element instanceof PsiWhiteSpace)) {
      element = file.findElementAt(offset-1);
      if (element == null) return null;
      parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    }

    return parameter;
  }

  public String getFamilyName() {
    return "Create Field for Parameter";
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  private void invoke(final Project project, Editor editor, PsiFile file, boolean isInteractive) {
    myParameter = forcedParameter ? myParameter : findParameterAtCursor(file, editor);
    if (!CodeInsightUtil.prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    final PsiType type = getType();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(VariableKind.FIELD, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    String fieldNameToCalc;
    boolean isFinalToCalc;

    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);

    if (isInteractive) {
      List<String> namesList = new ArrayList<String>();
      namesList.addAll(Arrays.asList(names));
      String defaultName = styleManager.propertyNameToVariableName(propertyName, VariableKind.FIELD);
      if (!namesList.contains(defaultName)) {
        namesList.add(0, defaultName);
      }
      else {
        Collections.swap(namesList, 0, namesList.indexOf(defaultName));
      }
      names = namesList.toArray(new String[namesList.size()]);

      CreateFieldFromParameterDialog dialog = new CreateFieldFromParameterDialog(
        project,
        names,
        type.getCanonicalText(), targetClass);
      dialog.show();

      if (!dialog.isOK()) return;

      fieldNameToCalc = dialog.getEnteredName();
      isFinalToCalc = dialog.isDeclareFinal();

      suggestedNameInfo.nameChoosen(fieldNameToCalc);
    }
    else {
      isFinalToCalc = true;
      fieldNameToCalc = names[0];
    }

    final boolean isFinal = isFinalToCalc;
    final String fieldName = fieldNameToCalc;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          PsiManager psiManager = PsiManager.getInstance(project);
          PsiElementFactory factory = psiManager.getElementFactory();

          PsiField field = factory.createField(fieldName, type);

          if (isFinal) {
            PsiModifierList modifierList = field.getModifierList();
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
          }

          PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();
          PsiCodeBlock methodBody = method.getBody();
          PsiStatement[] statements = methodBody.getStatements();

          int i = 0;

          PsiElement fieldAnchor = null;
          boolean insertBefore = false;


          for (; i < statements.length; i++) {
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
              else if (expression instanceof PsiAssignmentExpression) {
                PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
                PsiExpression lExpression = assignmentExpression.getLExpression();
                PsiExpression rExpression = assignmentExpression.getRExpression();

                if (!(lExpression instanceof PsiReferenceExpression)) break;
                if (!(rExpression instanceof PsiReferenceExpression)) break;

                PsiReferenceExpression lReference = (PsiReferenceExpression)lExpression;
                PsiReferenceExpression rReference = (PsiReferenceExpression)rExpression;

                PsiElement lElement = lReference.resolve();
                PsiElement rElement = rReference.resolve();

                if (!(lElement instanceof PsiField) || ((PsiField)lElement).getContainingClass() != targetClass) break;
                if (!(rElement instanceof PsiParameter)) break;

                if (myParameter.getTextRange().getStartOffset() < rElement.getTextRange().getStartOffset()) {
                  insertBefore = true;
                  fieldAnchor = lElement;
                  break;
                }

                fieldAnchor = lElement;

                continue;
              }
            }

            break;
          }

          if (fieldAnchor != null) {
            PsiVariable psiVariable = (PsiVariable)fieldAnchor;
            psiVariable.normalizeDeclaration();
          }

          String stmtText = fieldName + " = " + parameterName + ";";
          if (fieldName.equals(parameterName)) {
            stmtText = "this." + stmtText;
          }

          PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
          assignmentStmt = (PsiStatement)styleManager.reformat(assignmentStmt);

          boolean found = false;

          final PsiField[] fields = targetClass.getFields();
          for (int j = 0; j < fields.length; j++) {
            PsiField f = fields[j];
            if (f.getName().equals(field.getName())) {
              found = true;
              break;
            }
          }

          if (!found) {
            if (fieldAnchor != null) {
              if (insertBefore) {
                targetClass.addBefore(field, fieldAnchor);
              }
              else {
                targetClass.addAfter(field, fieldAnchor);
              }
            }
            else {
              targetClass.add(field);
            }
          }

          if (i == statements.length) {
            methodBody.add(assignmentStmt);
          }
          else {
            methodBody.addAfter(assignmentStmt, i > 0 ? statements[i - 1] : null);
          }
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    });
  }

  public boolean startInWriteAction() {
    return false;
  }
}
