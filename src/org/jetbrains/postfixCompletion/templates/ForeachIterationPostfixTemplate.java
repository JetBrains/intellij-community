package org.jetbrains.postfixCompletion.templates;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.IterableComponentTypeMacro;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.PsiPointerExpression;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.infrastructure.TemplateInfo;
import org.jetbrains.postfixCompletion.lookupItems.StatementPostfixLookupElement;

@TemplateInfo(
  templateName = "for",
  description = "Iterates over enumerable collection",
  example = "for (T item : collection)")
public final class ForeachIterationPostfixTemplate extends PostfixTemplate {
  @Override
  public LookupElement createLookupElement(@NotNull PostfixTemplateContext context) {
    PrefixExpressionContext expression = context.outerExpression();

    if (!context.executionContext.isForceMode) {
      PsiType expressionType = expression.expressionType;
      if (expressionType == null) return null;

      // for-statements can take expressions of array or Iterable<T>-derived types
      if (!(expressionType instanceof PsiArrayType) &&
          !InheritanceUtil.isInheritor(expressionType, CommonClassNames.JAVA_LANG_ITERABLE)) {
        return null;
      }
    }

    return new ForeachLookupElement(expression);
  }

  private static final class ForeachLookupElement extends StatementPostfixLookupElement<PsiForeachStatement> {
    public ForeachLookupElement(@NotNull PrefixExpressionContext context) {
      super("for", context);
    }

    @NotNull
    @Override
    protected PsiForeachStatement createNewStatement(@NotNull PsiElementFactory factory,
                                                     @NotNull PsiElement expression,
                                                     @NotNull PsiElement context) {
      PsiForeachStatement forStatement = (PsiForeachStatement)factory.createStatementFromText("for(T item:expr)", context);
      PsiExpression iteratedValue = forStatement.getIteratedValue();
      assert iteratedValue != null;
      iteratedValue.replace(expression);
      return forStatement;
    }

    @Override
    protected void postProcess(
      @NotNull final InsertionContext context, @NotNull PsiForeachStatement forStatement) {
      final Project project = context.getProject();
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
      final SmartPsiElementPointer<PsiForeachStatement> statementPointer =
        pointerManager.createSmartPsiElementPointer(forStatement);

      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          PsiForeachStatement statement = statementPointer.getElement();
          if (statement == null) return;

          // create template for iteration statement
          TemplateBuilderImpl builder = new TemplateBuilderImpl(statement);
          PsiParameter iterationParameter = statement.getIterationParameter();

          // store pointer to iterated value
          PsiExpression iteratedValue = statement.getIteratedValue();
          assert iteratedValue != null;
          final SmartPsiElementPointer<PsiExpression> valuePointer =
            pointerManager.createSmartPsiElementPointer(iteratedValue);

          // use standard macro, pass parameter expression with expression to iterate
          MacroCallNode iterableTypeExpression = new MacroCallNode(new IterableComponentTypeMacro());
          iterableTypeExpression.addParameter(new PsiPointerExpression(valuePointer));

          MacroCallNode nameExpression = new MacroCallNode(new SuggestVariableNameMacro());

          // setup placeholders and final position
          builder.replaceElement(iterationParameter.getTypeElement(), iterableTypeExpression, false);
          builder.replaceElement(iterationParameter.getNameIdentifier(), nameExpression, true);
          builder.setEndVariableAfter(statement.getRParenth());

          // todo: braces insertion?

          // create inline template and place caret before statement
          Template template = builder.buildInlineTemplate();

          Editor editor = context.getEditor();
          CaretModel caretModel = editor.getCaretModel();
          caretModel.moveToOffset(statement.getTextRange().getStartOffset());

          TemplateManager manager = TemplateManager.getInstance(project);
          manager.startTemplate(editor, template);
        }
      };

      context.setLaterRunnable(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              CommandProcessor.getInstance().runUndoTransparentAction(runnable);
            }
          });
        }
      });
    }
  }
}