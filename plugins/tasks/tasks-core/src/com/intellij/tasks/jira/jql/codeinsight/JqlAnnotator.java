package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.tasks.jira.jql.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Checks the following errors:
 * <ol>
 * <li>Not list operand was given inside 'IN' clauses or vice-versa
 * <li>constants 'empty' or 'null' weren't used inside 'IS' clauses or vice-versa
 * </ol>
 * @author Mikhail Golubev
 */
public class JqlAnnotator implements Annotator {
  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (element instanceof JqlSimpleClause) {
      JqlSimpleClause clause = (JqlSimpleClause)element;
      JqlTerminalClause.Type clauseType = clause.getType();
      JqlOperand operand = clause.getOperand();
      if (clauseType == null || operand == null) {
        return;
      }
      boolean isListLiteral = operand instanceof JqlList;
      boolean isListFunction = false;
      if (operand instanceof JqlFunctionCall) {
        JqlFunctionCall functionCall = (JqlFunctionCall)operand;
        JqlStandardFunction standardFunction = JqlStandardFunction.byName(functionCall.getFunctionName().getText());
        isListFunction = standardFunction != null && standardFunction.hasMultipleResults();
      }
      boolean hasListOperand = isListLiteral || isListFunction;
      if (clauseType.isListOperator() && !hasListOperand) {
        holder.createErrorAnnotation(operand, "Expecting list of values");
      } else if (!clauseType.isListOperator() && hasListOperand) {
        holder.createErrorAnnotation(operand, "Not expecting list of values here");
      }

      boolean hasEmptyOperand = operand instanceof JqlEmptyValue;
      boolean isEmptyClause = clauseType == JqlTerminalClause.Type.IS || clauseType == JqlTerminalClause.Type.IS_NOT;
      if (isEmptyClause && !hasEmptyOperand) {
        holder.createErrorAnnotation(operand, "Expecting 'empty' or 'null'");
      } else if (!isEmptyClause && hasEmptyOperand) {
        holder.createErrorAnnotation(operand, String.format("Not expecting '%s' here", operand.getText()));
      }
    }
  }
}
