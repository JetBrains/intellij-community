package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplateAcceptanceContext;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplatesManager;
import org.jetbrains.postfixCompletion.Infrastructure.PrefixExpressionContext;

import java.util.HashSet;
import java.util.Set;

public abstract class StatementPostfixLookupElement<TStatement extends PsiStatement>
  extends LookupElement {

  @NotNull private final Class<? extends PsiExpression> myExpressionType;
  @NotNull private final TextRange myExpressionRange;
  @NotNull private final String myLookupString;

  public StatementPostfixLookupElement(@NotNull final String lookupString,
                                       @NotNull final PrefixExpressionContext context) {
    myLookupString = lookupString;
    myExpressionType = context.expression.getClass();
    myExpressionRange = context.expression.getTextRange();
  }

  @NotNull @Override public String getLookupString() {
    return myLookupString;
  }

  @Override public boolean isWorthShowingInAutoPopup() {
    return true; // thx IDEA folks for implementing this!
  }

  @Override public Set<String> getAllLookupStrings() {
    final HashSet<String> set = new HashSet<>();

    // this hack prevents completion list from closing
    // when whole template name is typed
    set.add(myLookupString);
    set.add(myLookupString + " ");

    return set;
  }

  @Override public void handleInsert(final InsertionContext context) {
    // note: use 'postfix' string, to break expression like '0.postfix'
    final Document document = context.getDocument();
    final int startOffset = context.getStartOffset();
    document.replaceString(startOffset, context.getTailOffset(), "postfix");
    context.commitDocument();

    final PsiFile file = context.getFile();
    final PsiElement psiElement = file.findElementAt(startOffset);
    if (psiElement == null) return; // shit happens?

    final PostfixTemplatesManager manager =
      context.getProject().getComponent(PostfixTemplatesManager.class);
    final PostfixTemplateAcceptanceContext acceptanceContext = manager.isAvailable(psiElement, true);
    if (acceptanceContext == null) return; // yes, shit happens

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
        assert targetStatement != null : "targetStatement != null";

        final PsiExpression exprCopy = (PsiExpression) fixedContext.expression.copy();
        final TStatement newStatement = createNewStatement(psiElementFactory, exprCopy, file);

        PsiIfStatement newSt = (PsiIfStatement) targetStatement.replace(newStatement);

        final PsiDocumentManager documentManager =
          PsiDocumentManager.getInstance(context.getProject());
        documentManager.doPostponedOperationsAndUnblockDocument(document);

        final int offset = newSt.getTextRange().getEndOffset();
        context.getEditor().getCaretModel().moveToOffset(offset);

        // todo: custom caret pos?

        break; // ewww
      }
    }
  }

  @NotNull protected abstract TStatement createNewStatement(
    @NotNull final PsiElementFactory factory,
    @NotNull final PsiExpression expression,
    @NotNull final PsiFile context);
}