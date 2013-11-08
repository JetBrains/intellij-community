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

  public StatementPostfixLookupElement(
    @NotNull String lookupString, @NotNull PrefixExpressionContext context) {
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
    HashSet<String> set = new HashSet<String>();

    // this hack prevents completion list from closing
    // when whole template name is typed
    set.add(myLookupString);
    set.add(myLookupString + " ");

    return set;
  }

  @Override public void handleInsert(@NotNull InsertionContext context) {
    Document document = context.getDocument();
    int startOffset = context.getStartOffset();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(context.getProject());

    // note: use 'postfix' string, to break expression like '0.postfix'
    document.replaceString(startOffset, context.getTailOffset(), "postfix");
    context.commitDocument();

    PsiFile file = context.getFile();
    PsiElement psiElement = file.findElementAt(startOffset);
    if (psiElement == null) return; // shit happens?

    PostfixTemplatesManager manager =
      ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);
    PostfixTemplateAcceptanceContext acceptanceContext = manager.isAvailable(psiElement, true);
    if (acceptanceContext == null) return; // yes, shit happens

    for (PrefixExpressionContext expression : acceptanceContext.expressions) {
      PsiExpression expr = expression.expression;
      if (myExpressionType.isInstance(expr) &&
          expression.expressionRange.equals(myExpressionRange)) {

        // get facade and factory while all elements are physical and valid
        JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expr.getProject());
        PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

        // fix up expression before template expansion
        PrefixExpressionContext fixedContext = expression.fixUp();

        // get target statement to replace
        PsiStatement targetStatement = fixedContext.getContainingStatement();
        assert targetStatement != null : "targetStatement != null";

        PsiExpression exprCopy = (PsiExpression) fixedContext.expression.copy();
        TStatement newStatement = createNewStatement(psiElementFactory, exprCopy, file);

        //noinspection unchecked
        TStatement statement = (TStatement) targetStatement.replace(newStatement);

        documentManager.doPostponedOperationsAndUnblockDocument(document);

        postProcess(context, statement);
        break;
      }
    }
  }

  @NotNull protected abstract TStatement createNewStatement(
    @NotNull PsiElementFactory factory, @NotNull PsiExpression expression, @NotNull PsiFile context);

  protected void postProcess(@NotNull InsertionContext context, @NotNull TStatement statement) {
    int offset = statement.getTextRange().getEndOffset();
    context.getEditor().getCaretModel().moveToOffset(offset);
  }
}