package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ConcatenationToMessageFormatAction implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction");

  public String getFamilyName() {
    return "Replace Concatenation with Formatted Output";
  }

  public String getText() {
    return "Replace + with java.text.MessageFormat call";
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiBinaryExpression concatenation = getEnclosingConcatenation(file, editor);
    PsiManager manager = concatenation.getManager();
    StringBuffer formatString = new StringBuffer();
    List<PsiExpression> args = new ArrayList<PsiExpression>();
    ArrayList<PsiExpression> argsToCombine = new ArrayList<PsiExpression>();
    calculateFormatAndArguments(concatenation, formatString, args, argsToCombine);
    appendArgument(args, argsToCombine, formatString);

    PsiMethodCallExpression call = (PsiMethodCallExpression) manager.getElementFactory().createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    LOG.assertTrue(argumentList != null);
    PsiExpression formatArgument = manager.getElementFactory().createExpressionFromText("\"" + StringUtil.escapeStringCharacters(formatString.toString()) + "\"", null);
    argumentList.add(formatArgument);
    for (PsiExpression arg : args) {
      argumentList.add(arg);
    }
    call = (PsiMethodCallExpression) manager.getCodeStyleManager().shortenClassReferences(call);
    call = (PsiMethodCallExpression) manager.getCodeStyleManager().reformat(call);
    concatenation.replace(call);
  }

  private void calculateFormatAndArguments(PsiExpression expression, StringBuffer formatString, List<PsiExpression> args, List<PsiExpression> argsToCombine) throws IncorrectOperationException {
    if (expression == null) return;
    if (expression instanceof PsiBinaryExpression && expression.getType() != null &&
        expression.getType().equalsToText("java.lang.String") &&
        ((PsiBinaryExpression) expression).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
      calculateFormatAndArguments(((PsiBinaryExpression) expression).getLOperand(), formatString, args, argsToCombine);
      calculateFormatAndArguments(((PsiBinaryExpression) expression).getROperand(), formatString, args, argsToCombine);
    } else if (expression instanceof PsiLiteralExpression &&
        ((PsiLiteralExpression) expression).getValue() instanceof String) {
      appendArgument(args, argsToCombine, formatString);
      formatString.append(((PsiLiteralExpression) expression).getValue());
    } else {
      argsToCombine.add(expression);
    }
  }

  private void appendArgument(List<PsiExpression> args, List<PsiExpression> argsToCombine, StringBuffer formatString) throws IncorrectOperationException {
    if (argsToCombine.size() == 0) return;
    PsiExpression argument = argsToCombine.get(0);
    for (int i = 1; i < argsToCombine.size(); i++) {
      PsiBinaryExpression newArg = (PsiBinaryExpression) argument.getManager().getElementFactory().createExpressionFromText("a+b", null);
      newArg.getLOperand().replace(argument);
      PsiExpression rOperand = newArg.getROperand();
      assert rOperand != null;
      rOperand.replace(argsToCombine.get(i));
      argument = newArg;
    }

    formatString.append("{").append(args.size()).append("}");
    args.add(argument);
    argsToCombine.clear();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (PsiManager.getInstance(project).getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_4) < 0) return false;
    return getEnclosingConcatenation(file, editor) != null;
  }

  private PsiBinaryExpression getEnclosingConcatenation(PsiFile file, Editor editor) {
    PsiBinaryExpression concatenation = null;
    final PsiElement elementAt = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiLiteralExpression literal = PsiTreeUtil.getParentOfType(elementAt, PsiLiteralExpression.class, true);
    if (literal != null && literal.getValue() instanceof String) {
      PsiElement run = literal;
      while (run.getParent() instanceof PsiBinaryExpression &&
          ((PsiBinaryExpression) run.getParent()).getOperationSign().getTokenType() == JavaTokenType.PLUS) {
        concatenation = (PsiBinaryExpression) run.getParent();
        run = concatenation;
      }
    }
    return concatenation;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
