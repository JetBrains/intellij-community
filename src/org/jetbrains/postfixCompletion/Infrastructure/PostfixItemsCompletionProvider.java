package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.applet.AppletContext;

public final class PostfixItemsCompletionProvider
  extends CompletionProvider<CompletionParameters> {

  @NotNull
  public static final PostfixItemsCompletionProvider instance = new PostfixItemsCompletionProvider();

  private PostfixItemsCompletionProvider() { }

  public void addCompletions(
    @NotNull CompletionParameters parameters, ProcessingContext context,
    @NotNull CompletionResultSet resultSet) {

    final PostfixTemplatesManager templatesManager =
      ApplicationManager.getApplication().getComponent(PostfixTemplatesManager.class);

    final PsiElement positionElement = parameters.getPosition();

    if (templatesManager.getAvailableActions(positionElement)) {
      resultSet.addElement(new Foo());
    }
  }

  static class Foo extends com.intellij.codeInsight.lookup.LookupElement {


    @NotNull
    @Override
    public String getLookupString() {
      return "POSTFIX";
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
}
