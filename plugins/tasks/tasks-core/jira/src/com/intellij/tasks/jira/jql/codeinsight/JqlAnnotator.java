package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.tasks.jira.jql.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.CONSTANT;

/**
 * At first, it checks the following errors:
 * <ol>
 * <li>not-list operand was given inside 'IN' clauses and vice-versa
 * <li>constants 'empty' or 'null' weren't used inside 'IS' clauses and vice-versa
 * </ol>
 *
 *
 * It also highlights JQL identifiers (fields and function names) differently
 * from unquoted strings.
 *
 * @author Mikhail Golubev
 */
public class JqlAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, final @NotNull AnnotationHolder holder) {
    // error checks
    element.accept(new JqlElementVisitor() {
      @Override
      public void visitEmptyValue(JqlEmptyValue emptyValue) {
        JqlSimpleClause clause = PsiTreeUtil.getParentOfType(emptyValue, JqlSimpleClause.class);
        if (clause != null && !isEmptyClause(clause)) {
          holder.createErrorAnnotation(emptyValue, String.format("Not expecting '%s' here", emptyValue.getText()));
        }
      }

      @Override
      public void visitJqlList(JqlList list) {
        JqlSimpleClause clause = PsiTreeUtil.getParentOfType(list, JqlSimpleClause.class);
        if (clause != null && !isListClause(clause)) {
          holder.createErrorAnnotation(list, String.format("Not expecting list of values here"));
        }
      }

      @Override
      public void visitJqlSimpleClause(JqlSimpleClause clause) {
        JqlOperand operand = clause.getOperand();
        if (operand == null) {
          return;
        }
        boolean operandIsListLiteral = operand instanceof JqlList;
        boolean operandIsListFunction = false;
        if (operand instanceof JqlFunctionCall) {
          JqlFunctionCall functionCall = (JqlFunctionCall)operand;
          JqlStandardFunction standardFunction = JqlStandardFunction.byName(functionCall.getFunctionName().getText());
          operandIsListFunction = standardFunction != null && standardFunction.hasMultipleResults();
        }
        boolean hasListOperand = operandIsListLiteral || operandIsListFunction;
        if (isListClause(clause) && !hasListOperand) {
          holder.createErrorAnnotation(operand, "Expecting list of values here");
        }

        boolean hasEmptyOperand = operand instanceof JqlEmptyValue;
        if (isEmptyClause(clause) && !hasEmptyOperand) {
          holder.createErrorAnnotation(operand, "Expecting 'empty' or 'null' here");
        }
      }

      @Override
      public void visitJqlIdentifier(JqlIdentifier identifier) {
        Annotation annotation = holder.createInfoAnnotation(identifier, null);
        annotation.setEnforcedTextAttributes(CONSTANT.getDefaultAttributes());
      }
    });
  }

  private static boolean isEmptyClause(JqlTerminalClause clause) {
    JqlTerminalClause.Type clauseType = clause.getType();
    return clauseType == JqlTerminalClause.Type.IS || clauseType == JqlTerminalClause.Type.IS_NOT;
  }

  private static boolean isListClause(JqlTerminalClause clause) {
    JqlTerminalClause.Type clauseType = clause.getType();
    return clauseType != null && clauseType.isListOperator();
  }
}
