package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PostfixTemplatesManager implements ApplicationComponent {
  @NotNull private final List<TemplateProviderInfo> myProviders;

  public PostfixTemplatesManager(@NotNull final PostfixTemplateProvider[] providers) {
    myProviders = new ArrayList<>();

    for (PostfixTemplateProvider provider : providers) {
      final TemplateProvider annotation =
        provider.getClass().getAnnotation(TemplateProvider.class);
      if (annotation != null)
        myProviders.add(new TemplateProviderInfo(provider, annotation));
    }
  }

  private static class TemplateProviderInfo {
    @NotNull public final PostfixTemplateProvider provider;
    @NotNull public final TemplateProvider annotation;

    public TemplateProviderInfo(@NotNull final PostfixTemplateProvider provider,
                                @NotNull final TemplateProvider annotation) {
      this.provider = provider;
      this.annotation = annotation;
    }
  }

  @Nullable public final PostfixTemplateAcceptanceContext isAvailable(
    @NotNull final PsiElement positionElement, boolean forceMode) {

    if (positionElement instanceof PsiIdentifier) {
      final PsiReferenceExpression referenceExpression =
        PsiTreeUtil.getParentOfType(positionElement, PsiReferenceExpression.class);
      if (referenceExpression == null) return null;

      // easy case: 'expr.postfix'
      final PsiExpression qualifier = referenceExpression.getQualifierExpression();
      if (qualifier != null) {
        return new PostfixTemplateAcceptanceContext(
          referenceExpression, qualifier, forceMode) {

          @Override @NotNull
          public PrefixExpressionContext fixUpExpression(
            final @NotNull PrefixExpressionContext context) {

            // replace 'expr.postfix' with 'expr'
            final PsiElement parent = context.expression.getParent();
            if (parent instanceof PsiReferenceExpression && parent == this.referenceExpression) {
              final PsiExpression newExpression =
                (PsiExpression) this.referenceExpression.replace(context.expression);
              return new PrefixExpressionContext(this, newExpression);
            }

            return context;
          }
        };
      }

      // hard case: 'x > 0.if' (two expression statements, broken literal)
      if (referenceExpression.getFirstChild() instanceof PsiReferenceParameterList &&
          referenceExpression.getLastChild() == positionElement) {
        final PsiExpressionStatement statement =
          PsiTreeUtil.getParentOfType(referenceExpression, PsiExpressionStatement.class);
        if (statement == null) return null;

        // todo: will it handle 'a instanceof T.if' - ES;Error;ES;?
        final PsiStatement prevStatement =
          PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
        if (!(prevStatement instanceof PsiExpressionStatement)) return null;

        final PsiElement lastErrorChild = prevStatement.getLastChild();
        if (lastErrorChild instanceof PsiErrorElement) {
          PsiExpression expression = ((PsiExpressionStatement) prevStatement).getExpression();
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
              return new PostfixTemplateAcceptanceContext(
                referenceExpression, brokenLiteral, forceMode) {

                @Override @NotNull
                public PrefixExpressionContext fixUpExpression(@NotNull PrefixExpressionContext context) {

                  // todo: unbroke literal
                  // todo: remove separated expression statement

                  return context;
                }
              };
            }
          }
        }
      }
    }

    return null;
  }

  @NotNull public List<LookupElement> collectTemplates(
    @NotNull final PostfixTemplateAcceptanceContext context) {

    final List<LookupElement> elements = new ArrayList<>();

    for (final TemplateProviderInfo providerInfo : myProviders) {
      providerInfo.provider.createItems(context, elements);
    }

    return elements;
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