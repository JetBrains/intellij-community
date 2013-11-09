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

public abstract class PostfixLookupElement<TPsiElement extends PsiElement> extends LookupElement {
  @NotNull protected final Class<? extends PsiExpression> myExpressionType;
  @NotNull protected final TextRange myExpressionRange;
  @NotNull protected final String myLookupString;

  public PostfixLookupElement(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    myExpressionType = context.expression.getClass();
    myLookupString = lookupString;
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
      if (myExpressionType.isInstance(expression.expression) &&
          expression.expressionRange.equals(myExpressionRange)) {

        TPsiElement newElement = handlePostfixInsert(context, expression);
        assert newElement.isPhysical() : "newElement.isPhysical()";

        SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
        SmartPsiElementPointer<TPsiElement> pointer = pointerManager.createSmartPsiElementPointer(newElement);

        documentManager.doPostponedOperationsAndUnblockDocument(document);

        newElement = pointer.getElement();
        if (newElement != null) {
          postProcess(context, newElement);
        }

        break;
      }
    }
  }

  @NotNull protected abstract TPsiElement handlePostfixInsert(
    @NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext);

  protected abstract void postProcess(
    @NotNull InsertionContext context, @NotNull TPsiElement element);
}
