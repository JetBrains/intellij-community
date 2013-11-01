package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PostfixTemplatesManager implements ApplicationComponent {
  @NotNull private final PostfixTemplateProvider[] myTemplateProviders;

  public PostfixTemplatesManager(@NotNull final PostfixTemplateProvider[] providers) {
    myTemplateProviders = providers;
  }

  public boolean getAvailableActions(@NotNull final PsiElement positionElement) {
    if (positionElement instanceof PsiIdentifier) {
      final PsiReferenceExpression referenceExpression =
        PsiTreeUtil.getParentOfType(positionElement, PsiReferenceExpression.class);
      if (referenceExpression != null) {

        // simple case: someExpression.postfix
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null) {
          doOtherWork(referenceExpression, qualifier);
          return true;
        }

        // hard case: x > 0.if (two expression statements)
        if (referenceExpression.getFirstChild() instanceof PsiReferenceParameterList) {
          final PsiExpressionStatement statement =
            PsiTreeUtil.getParentOfType(referenceExpression, PsiExpressionStatement.class);
          if (statement != null) {
            final PsiExpressionStatement prevStatement =
              PsiTreeUtil.getPrevSiblingOfType(statement, PsiExpressionStatement.class);
            if (prevStatement != null){
              // look for 'non-terminated expression-statement' error
              final PsiElement lastErrorChild = prevStatement.getLastChild();
              if (lastErrorChild instanceof PsiErrorElement) {
                PsiExpression expression = prevStatement.getExpression();
                if (prevStatement.getFirstChild() == expression &&
                    lastErrorChild.getPrevSibling() == expression) {

                  PsiLiteralExpression brokenLiteral = null;
                  do {
                    // look for double literal broken by dot at end
                    if (expression instanceof PsiLiteralExpression) {
                      final PsiJavaToken token = PsiTreeUtil.getChildOfType(expression, PsiJavaToken.class);
                      if (token != null
                          && token.getTokenType() == JavaTokenType.DOUBLE_LITERAL
                          && token.getText().endsWith(".")) {
                        brokenLiteral = (PsiLiteralExpression) expression;
                        break;
                      }
                    }

                    // skip current expression and look its last inner expression
                    final PsiElement last = expression.getLastChild();
                    if (last instanceof PsiExpression) expression = (PsiExpression) last;
                    else expression = PsiTreeUtil.getPrevSiblingOfType(last, PsiExpression.class);
                  } while (expression != null);

                  if (brokenLiteral != null) {
                    return true;
                  }
                }
              }
            }
          }
        }
      }
    }


    //if (positionElement instanceof )


    return true;
  }

  private void doOtherWork(PsiReferenceExpression reference, PsiExpression expression) {

    for (final PostfixTemplateProvider templateProvider : myTemplateProviders) {

    }

  }

  @Nullable
  private <T> T match(Object obj, Class<T> type) {
    if (type.isInstance(obj)) return type.cast(obj);
    return null;
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() { }

  @NotNull
  @Override
  public String getComponentName() {
    return PostfixTemplatesManager.class.getTypeName();
  }
}
