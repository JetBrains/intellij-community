package org.jetbrains.postfixCompletion.LookupItems;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class PostfixLookupItem extends com.intellij.codeInsight.lookup.LookupElement {
  @NotNull private final String myLookupString;

  public PostfixLookupItem(@NotNull final String lookupString) {
    this.myLookupString = lookupString;
  }

  @NotNull @Override
  public String getLookupString() {
    return myLookupString;
  }

  @Override
  public void handleInsert(InsertionContext context) {
    //context.commitDocument();
    //final char completionChar = context.getCompletionChar();
    final int startOffset = context.getStartOffset();
    final int endOffset = context.getTailOffset();

    context.getDocument().replaceString(startOffset, endOffset, "__");
    context.commitDocument();



    final PsiElement psiElement = context.getFile().findElementAt(startOffset);

    if (psiElement instanceof PsiIdentifier) {


      final PsiElement parent = psiElement.getParent();
      if (parent instanceof PsiReferenceExpression) {
        final PsiExpression qualifierExpression = ((PsiReferenceExpression) parent).getQualifierExpression();
        if (qualifierExpression != null) {


          PsiElement parent1 = parent.getParent();
          if (parent1 instanceof PsiExpressionStatement) {
            JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(qualifierExpression.getProject());
            PsiElementFactory psiElementFactory = psiFacade.getElementFactory();

            PsiIfStatement psiStatement = (PsiIfStatement)
              psiElementFactory.createStatementFromText("if (expr) { }", qualifierExpression);

            PsiExpression condition = psiStatement.getCondition();
            assert condition != null;
            condition.replace(qualifierExpression);

            PsiIfStatement newSt = (PsiIfStatement) parent1.replace(psiStatement);


          }
        }
      }
    }
  }
}