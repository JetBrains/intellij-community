package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CreateFieldFromParameterAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.CreateFieldFromParameterAction");
  private PsiParameter myParameter;

  private  PsiType getType() {
    if (myParameter == null) return null;
    PsiType type = myParameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    return type;
  }

  public String getText() {
    return CodeInsightBundle.message("intention.create.field.from.parameter.text", myParameter.getName());
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    myParameter = findParameterAtCursor(file, editor);
    final PsiType type = getType();
    PsiClass targetClass = myParameter == null ? null : PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    return
      myParameter != null
      && myParameter.isValid()
      && myParameter.getDeclarationScope() instanceof PsiMethod
      && ((PsiMethod)myParameter.getDeclarationScope()).getBody() != null
      && myParameter.getManager().isInProject(myParameter)
      && type != null
      && type.isValid()
      && !isParameterAssignedToField(myParameter)
      && targetClass != null
      && !targetClass.isInterface()
      ;
  }

  static boolean isParameterAssignedToField(final PsiParameter parameter) {
    final PsiSearchHelper searchHelper = parameter.getManager().getSearchHelper();
    final PsiReference[] references = searchHelper.findReferences(parameter, new LocalSearchScope(parameter.getDeclarationScope()), false);
    for (PsiReference reference : references) {
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

  static PsiParameter findParameterAtCursor(final PsiFile file, final Editor editor) {
    final int offset = editor.getCaretModel().getOffset();
    final PsiParameterList parameterList = PsiTreeUtil.findElementOfClassAtOffset(file, offset, PsiParameterList.class, false);
    if (parameterList == null) return null;
    final PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      final TextRange range = parameter.getTextRange();
      if (range.getStartOffset() <= offset && offset <= range.getEndOffset()) return parameter;
    }
    return null;
  }

  public String getFamilyName() {
    return CodeInsightBundle.message("intention.create.field.from.parameter.family");
  }

  public void invoke(Project project, Editor editor, PsiFile file) {
    invoke(project, editor, file, !ApplicationManager.getApplication().isUnitTestMode());
  }

  private void invoke(final Project project, Editor editor, PsiFile file, boolean isInteractive) {
    myParameter = findParameterAtCursor(file, editor);
    if (!CodeInsightUtil.prepareFileForWrite(myParameter.getContainingFile())) return;

    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();
    final PsiType type = getType();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    final String parameterName = myParameter.getName();
    String propertyName = styleManager.variableNameToPropertyName(parameterName, VariableKind.PARAMETER);

    String fieldNameToCalc;
    boolean isFinalToCalc;

    final PsiClass targetClass = PsiTreeUtil.getParentOfType(myParameter, PsiClass.class);
    final PsiMethod method = (PsiMethod)myParameter.getDeclarationScope();

    final boolean isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);

    VariableKind kind = isMethodStatic ? VariableKind.STATIC_FIELD : VariableKind.FIELD;
    SuggestedNameInfo suggestedNameInfo = styleManager.suggestVariableName(kind, propertyName, null, type);
    String[] names = suggestedNameInfo.names;

    if (isInteractive) {
      List<String> namesList = new ArrayList<String>();
      namesList.addAll(Arrays.asList(names));
      String defaultName = styleManager.propertyNameToVariableName(propertyName, kind);
      if (namesList.contains(defaultName)) {
        Collections.swap(namesList, 0, namesList.indexOf(defaultName));
      }
      else {
        namesList.add(0, defaultName);
      }
      names = namesList.toArray(new String[namesList.size()]);

      boolean myBeFinal = method.isConstructor();
      CreateFieldFromParameterDialog dialog = new CreateFieldFromParameterDialog(
        project,
        names,
        type.getCanonicalText(), targetClass, myBeFinal);
      dialog.show();

      if (!dialog.isOK()) return;

      fieldNameToCalc = dialog.getEnteredName();
      isFinalToCalc = dialog.isDeclareFinal();

      suggestedNameInfo.nameChoosen(fieldNameToCalc);
    }
    else {
      isFinalToCalc = !isMethodStatic;
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
          PsiModifierList modifierList = field.getModifierList();
          modifierList.setModifierProperty(PsiModifier.STATIC, isMethodStatic);
          modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

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
                @NonNls String text = methodCallExpression.getMethodExpression().getText();

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
            @NonNls String prefix = isMethodStatic ? targetClass.getName() == null ? "" : targetClass.getName() + "." : "this.";
            stmtText = prefix + stmtText;
          }

          PsiStatement assignmentStmt = factory.createStatementFromText(stmtText, methodBody);
          assignmentStmt = (PsiStatement)styleManager.reformat(assignmentStmt);

          boolean found = false;

          final PsiField[] fields = targetClass.getFields();
          for (PsiField f : fields) {
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
