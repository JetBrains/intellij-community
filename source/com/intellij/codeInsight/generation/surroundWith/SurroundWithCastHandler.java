package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;
import java.util.LinkedHashSet;

class SurroundWithCastHandler implements SurroundExpressionHandler {
  public boolean isApplicable(PsiExpression expr) {
    return true;
  }

  public TextRange surroundExpression(final Project project, final Editor editor, PsiExpression expr) throws IncorrectOperationException {
    PsiType[] types = GuessManager.getInstance(project).guessTypeToCast(expr);
    final Template template = generateTemplate(project, expr.getText(), types);
    TextRange range = expr.getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());
    int offset = range.getStartOffset();
    editor.getCaretModel().moveToOffset(offset);
    editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    TemplateManager.getInstance(project).startTemplate(editor, template);
    return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
  }

  private Template generateTemplate(Project project, String exprText, final PsiType[] suggestedTypes) {
    final TemplateManager templateManager = TemplateManager.getInstance(project);
    final Template template = templateManager.createTemplate("", "");
    template.setToReformat(true);

    LinkedHashSet itemSet = new LinkedHashSet();
    for (int i = 0; i < suggestedTypes.length; i++) {
      PsiType type = suggestedTypes[i];
      LookupItemUtil.addLookupItem(itemSet, type, "");
    }
    final LookupItem[] lookupItems = (LookupItem[]) itemSet.toArray(new LookupItem[itemSet.size()]);

    final Result result = suggestedTypes.length > 0 ? new PsiTypeResult(suggestedTypes[0], PsiManager.getInstance(project)) : null;

    Expression expr = new Expression() {
      public LookupItem[] calculateLookupItems(ExpressionContext context) {
        return lookupItems.length > 1 ? lookupItems : null;
      }

      public Result calculateResult(ExpressionContext context) {
        return result;
      }

      public Result calculateQuickResult(ExpressionContext context) {
        return null;
      }
    };
    template.addTextSegment("((");
    template.addVariable("type", expr, expr, true);
    template.addTextSegment(")" + exprText + ")");
    template.addEndVariable();

    return template;
  }
}