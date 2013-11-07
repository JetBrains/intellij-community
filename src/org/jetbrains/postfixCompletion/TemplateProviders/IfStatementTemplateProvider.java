package org.jetbrains.postfixCompletion.TemplateProviders;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplatesManager;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProvider;
import org.jetbrains.postfixCompletion.LookupItems.PostfixLookupItem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TemplateProvider(
  templateName = "if",
  description = "Checks boolean expression to be 'true'",
  example = "if (expr)")
public class IfStatementTemplateProvider extends TemplateProviderBase {
  @Override public void createItems(
    @NotNull final PostfixTemplateAcceptanceContext context,
    @NotNull final List<LookupElement> consumer) {

    // todo: handle Boolean?
    // todo: handle force mode
    // todo: handle unknown type?

    for (final PrefixExpressionContext expressionContext : context.expressions) {

      if (isBooleanExpression(expressionContext)) {
        final IfLookupElement lookupElement = new IfLookupElement(expressionContext);
        consumer.add(lookupElement);
        break;
      }
    }
  }

  private static boolean isBooleanExpression(
    @NotNull final PrefixExpressionContext context) {

    final PsiType expressionType = context.expressionType;
    if (expressionType != null) {

      if (PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
        return true;
      }

    } else {
      final PsiExpression expression = context.expression;
      if (expression instanceof PsiBinaryExpression) {
        final PsiJavaToken operationSign = ((PsiBinaryExpression) expression).getOperationSign();
        final IElementType tokenType = operationSign.getTokenType();
        if (tokenType == JavaTokenType.GE || // x >= y
            tokenType == JavaTokenType.LE || // x <= y
            tokenType == JavaTokenType.LT || // x < y
            tokenType == JavaTokenType.GT || // x > y
            tokenType == JavaTokenType.NE || // x != y
            tokenType == JavaTokenType.EQEQ || // x == y
            tokenType == JavaTokenType.ANDAND || // x && y
            //tokenType == JavaTokenType.AND || // x & y
            tokenType == JavaTokenType.OROR || // x || y
            //tokenType == JavaTokenType.OR || // x | y
            //tokenType == JavaTokenType.XOR || // x ^ y
            tokenType == JavaTokenType.INSTANCEOF_KEYWORD // todo: make it work
          ) {
          return true; // TODO: other
        }
      }
    }

    return false;
  }

  private static final class IfLookupElement extends PostfixLookupItem {
    @NotNull private final Class<? extends PsiExpression> myExpressionType;
    @NotNull private final TextRange myExpressionRange;

    //private final PsiExpression myFoo;

    public IfLookupElement(@NotNull final PrefixExpressionContext context) {
      super("if");
      myExpressionType = context.expression.getClass();
      myExpressionRange = context.expression.getTextRange();
    }

    @Override
    public boolean isWorthShowingInAutoPopup() {
      final boolean worthShowingInAutoPopup = super.isWorthShowingInAutoPopup();
      return true;
    }

    @Override
    public Set<String> getAllLookupStrings() {
      final Set<String> xs = new HashSet<>();
      xs.add("if");
      xs.add("if ");
      xs.add("if{");
      return xs;
    }



    @Override
    public void handleInsert(final InsertionContext context) {



      final PostfixTemplatesManager templatesManager =
        ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);

      final int
        startOffset = context.getStartOffset(),
        endOffset = context.getTailOffset();

      // note: use 'postfix' string, to break expression like '0.postfix'
      context.getDocument().replaceString(startOffset, endOffset, "postfix");
      context.commitDocument();

      final PsiFile file = context.getFile();
      final PsiElement psiElement = file.findElementAt(startOffset);
      if (psiElement == null) return;

      final PostfixTemplateAcceptanceContext acceptanceContext = templatesManager.isAvailable(psiElement, true);
      if (acceptanceContext == null) return;

      for (final PrefixExpressionContext expression : acceptanceContext.expressions) {
        final PsiExpression expr = expression.expression;
        if (myExpressionType.isInstance(expr) && expr.getTextRange().equals(myExpressionRange)) {

          // get facade and factory while all elements are physical and valid
          final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expr.getProject());
          final PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

          // fix up expression before template expansion
          final PrefixExpressionContext fixedContext = expression.fixUp();

          // get target statement to replace
          final PsiStatement targetStatement = fixedContext.getContainingStatement();
          assert targetStatement != null : "impossible";

          final PsiIfStatement psiStatement = (PsiIfStatement)
            psiElementFactory.createStatementFromText("if(expr){CARET;}", file);

          // already physical
          final PsiExpression condition = psiStatement.getCondition();
          assert condition != null;
          condition.replace(fixedContext.expression.copy());

          PsiIfStatement newSt = (PsiIfStatement) targetStatement.replace(psiStatement);

          final PsiStatement thenBranch = newSt.getThenBranch();
          if (thenBranch instanceof PsiBlockStatement) {
            final PsiStatement caret = ((PsiBlockStatement) thenBranch)
              .getCodeBlock().getStatements()[0];


            final TextRange textRange = caret.getTextRange();
            final RangeMarker rangeMarker = context.getDocument().createRangeMarker(textRange);

            context.setLaterRunnable(new Runnable() {
              @Override
              public void run() {
                if (!rangeMarker.isValid()) {
                  return;
                }

                //PsiDocumentManager.getInstance(null)
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    context.getEditor().getCaretModel().moveToOffset(rangeMarker.getEndOffset());
                    context.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
                  }
                });
              }
            });


            //caret.delete();
          }

          // do magic
          break;
        }
      }
    }
  }
}

