package com.intellij.tasks.jira.jql.codeinsight;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.tasks.TaskBundle;
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
          holder.newAnnotation(HighlightSeverity.ERROR, String.format(TaskBundle.message("inspection.message.not.expecting.s.here"), emptyValue.getText())).create();
        }
      }

      @Override
      public void visitJqlList(JqlList list) {
        JqlSimpleClause clause = PsiTreeUtil.getParentOfType(list, JqlSimpleClause.class);
        if (clause != null && !isListClause(clause)) {
          holder.newAnnotation(HighlightSeverity.ERROR, TaskBundle.message("inspection.message.not.expecting.list.values.here")).create();
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
          holder.newAnnotation(HighlightSeverity.ERROR, TaskBundle.message("inspection.message.expecting.list.values.here")).range(operand).create();
        }

        boolean hasEmptyOperand = operand instanceof JqlEmptyValue;
        if (isEmptyClause(clause) && !hasEmptyOperand) {
          holder.newAnnotation(HighlightSeverity.ERROR, TaskBundle.message("inspection.message.expecting.empty.or.null.here")).range(operand).create();
        }
      }

      @Override
      public void visitJqlIdentifier(JqlIdentifier identifier) {
        TextAttributes attributes = CONSTANT.getDefaultAttributes();
        if (attributes != null) {
          holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
          .enforcedTextAttributes(attributes).create();
        }
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
