package org.jetbrains.postfixCompletion.lookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.infrastructure.PostfixExecutionContext;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplateContext;
import org.jetbrains.postfixCompletion.infrastructure.PostfixTemplatesService;
import org.jetbrains.postfixCompletion.infrastructure.PrefixExpressionContext;

import java.util.HashSet;
import java.util.Set;

public abstract class PostfixLookupElementBase<T extends PsiElement> extends LookupElement {
  @NotNull private final PostfixExecutionContext myExecutionContext;
  @NotNull private final Class<?> myExpressionType;
  @NotNull private final TextRange myExpressionRange;
  @NotNull private final String myLookupString;
  private final int myContextIndex;

  public PostfixLookupElementBase(@NotNull String lookupString, @NotNull PrefixExpressionContext context) {
    myExecutionContext = context.parentContext.executionContext;
    myExpressionType = context.expression.getClass();
    myLookupString = lookupString;
    myExpressionRange = context.expressionRange;
    myContextIndex = context.parentContext.expressions().indexOf(context);
  }

  @NotNull
  @Override
  public final String getLookupString() {
    return myLookupString;
  }

  @Override
  public final boolean isWorthShowingInAutoPopup() {
    return true; // thx IDEA folks for implementing this!
  }

  @Override
  public Set<String> getAllLookupStrings() {
    HashSet<String> set = new HashSet<String>();

    // this hack prevents completion list from closing
    // when whole template name is typed
    set.add(myLookupString);
    set.add(myLookupString + " ");

    return set;
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    Document document = context.getDocument();
    int startOffset = context.getStartOffset();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(context.getProject());

    // note: use 'postfix' string, to break expression like '0.postfix'
    document.replaceString(startOffset, context.getTailOffset(), myExecutionContext.dummyIdentifier);
    context.commitDocument();

    PsiFile file = context.getFile();
    PsiElement psiElement = file.findElementAt(startOffset);
    if (psiElement == null) return; // shit happens?

    PostfixTemplatesService templatesService = PostfixTemplatesService.getInstance();
    if (templatesService == null) return;
    PostfixTemplateContext templateContext = templatesService.isAvailable(psiElement, myExecutionContext);
    if (templateContext == null) return; // yes, shit happens

    PrefixExpressionContext originalExpression = findOriginalContext(templateContext);
    if (originalExpression != null) {
      T newElement = handlePostfixInsert(context, originalExpression);
      assert newElement.isPhysical() : "newElement.isPhysical()";

      SmartPointerManager pointerManager = SmartPointerManager.getInstance(context.getProject());
      SmartPsiElementPointer<T> pointer = pointerManager.createSmartPsiElementPointer(newElement);

      documentManager.doPostponedOperationsAndUnblockDocument(document);

      newElement = pointer.getElement();
      if (newElement != null) {
        postProcess(context, newElement);
      }
    }
  }

  @Nullable
  private PrefixExpressionContext findOriginalContext(@NotNull PostfixTemplateContext context) {
    for (PrefixExpressionContext expressionContext : context.expressions()) {
      if (myExpressionType.isInstance(expressionContext.expression) &&
          expressionContext.expressionRange.equals(myExpressionRange)) {
        return expressionContext;
      }
    }

    int index = 0;
    for (PrefixExpressionContext expressionContext : context.expressions()) {
      if (myContextIndex == index++) {
        return expressionContext;
      }
    }

    return null;
  }

  @NotNull
  protected abstract T handlePostfixInsert(@NotNull InsertionContext context, @NotNull PrefixExpressionContext expressionContext);

  protected abstract void postProcess(@NotNull InsertionContext context, @NotNull T element);
}
