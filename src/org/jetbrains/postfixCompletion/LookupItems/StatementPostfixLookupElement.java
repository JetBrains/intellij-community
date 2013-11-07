package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import org.jetbrains.annotations.*;
import org.jetbrains.postfixCompletion.Infrastructure.*;

import java.util.*;

public abstract class StatementPostfixLookupElement<TStatement extends PsiStatement>
  extends LookupElement {

  @NotNull private final Class<? extends PsiExpression> myExpressionType;
  @NotNull private final TextRange myExpressionRange;
  @NotNull private final String myLookupString;

  public StatementPostfixLookupElement(@NotNull final String lookupString,
                                       @NotNull final PrefixExpressionContext context) {
    myLookupString = lookupString;
    myExpressionType = context.expression.getClass();
    myExpressionRange = context.expressionRange;
  }

  @NotNull @Override public final String getLookupString() {
    return myLookupString;
  }

  @Override public final boolean isWorthShowingInAutoPopup() {
    return true; // thx IDEA folks for implementing this!
  }

  @Override public final Set<String> getAllLookupStrings() {
    final HashSet<String> set = new HashSet<>();

    // this hack prevents completion list from closing
    // when whole template name is typed
    set.add(myLookupString);
    set.add(myLookupString + " ");

    return set;
  }

  @Override public void handleInsert(@NotNull final InsertionContext context) {
    // note: use 'postfix' string, to break expression like '0.postfix'
    final Document document = context.getDocument();
    final int startOffset = context.getStartOffset();
    document.replaceString(startOffset, context.getTailOffset(), "postfix");
    context.commitDocument();

    final PsiFile file = context.getFile();
    final PsiElement psiElement = file.findElementAt(startOffset);
    if (psiElement == null) return; // shit happens?

    final PostfixTemplatesManager manager =
      ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);
    final PostfixTemplateAcceptanceContext acceptanceContext = manager.isAvailable(psiElement, true);
    if (acceptanceContext == null) return; // yes, shit happens

    for (final PrefixExpressionContext expression : acceptanceContext.expressions) {
      final PsiExpression expr = expression.expression;
      if (myExpressionType.isInstance(expr) &&
          expression.expressionRange.equals(myExpressionRange)) {

        // get facade and factory while all elements are physical and valid
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expr.getProject());
        final PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

        // fix up expression before template expansion
        final PrefixExpressionContext fixedContext = expression.fixUp();

        // get target statement to replace
        final PsiStatement targetStatement = fixedContext.getContainingStatement();
        assert targetStatement != null : "targetStatement != null";

        final PsiExpression exprCopy = (PsiExpression) fixedContext.expression.copy();
        final TStatement newStatement = createNewStatement(psiElementFactory, exprCopy, file);

        //noinspection unchecked
        TStatement newSt = (TStatement) targetStatement.replace(newStatement);

        final PsiDocumentManager documentManager =
          PsiDocumentManager.getInstance(context.getProject());
        documentManager.doPostponedOperationsAndUnblockDocument(document);

        final int offset = newSt.getTextRange().getEndOffset();
        context.getEditor().getCaretModel().moveToOffset(offset);

        postProcess(context, newSt);
        break;
      }
    }
  }

  @NotNull protected abstract TStatement createNewStatement(
    @NotNull final PsiElementFactory factory,
    @NotNull final PsiExpression expression,
    @NotNull final PsiFile context);

  protected void postProcess(@NotNull final InsertionContext context,
                             @NotNull final TStatement statement) {
    // do nothing
  }
}