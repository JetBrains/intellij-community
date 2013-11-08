package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

public final class PostfixTemplatesManager implements ApplicationComponent {
  @NotNull private final List<TemplateProviderInfo> myProviders;

  public PostfixTemplatesManager(@NotNull PostfixTemplateProvider[] providers) {
    myProviders = new ArrayList<TemplateProviderInfo>();

    for (PostfixTemplateProvider provider : providers) {
      TemplateProvider annotation = provider.getClass().getAnnotation(TemplateProvider.class);
      if (annotation != null)
        myProviders.add(new TemplateProviderInfo(provider, annotation));
    }
  }

  private static class TemplateProviderInfo {
    @NotNull public final PostfixTemplateProvider provider;
    @NotNull public final TemplateProvider annotation;

    public TemplateProviderInfo(@NotNull PostfixTemplateProvider provider, @NotNull TemplateProvider annotation) {
      this.provider = provider;
      this.annotation = annotation;
    }
  }

  @Nullable public final PostfixTemplateAcceptanceContext isAvailable(
      @NotNull PsiElement positionElement, boolean forceMode) {

    // postfix name always is identifier
    if (!(positionElement instanceof PsiIdentifier)) return null;

    final PsiElement parent = positionElement.getParent();
    if (parent instanceof PsiReferenceExpression) {
      PsiReferenceExpression reference = (PsiReferenceExpression) parent;

      // easy case: 'expr.postfix'
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null) {
        return new PostfixTemplateAcceptanceContext(reference, qualifier, forceMode) {
          @Override @NotNull public PrefixExpressionContext fixUpExpression(@NotNull PrefixExpressionContext context) {
            // replace 'expr.postfix' with 'expr'
            PsiElement parent = context.expression.getParent();
            if (parent instanceof PsiReferenceExpression && parent == this.postfixReference) {
              PsiExpression newExpression = (PsiExpression) this.postfixReference.replace(context.expression);
              return new PrefixExpressionContext(this, newExpression);
            }

            return context;
          }
        };
      }

      // hard case: 'x > 0.if' (two expression statements, broken literal)
      if (reference.getFirstChild() instanceof PsiReferenceParameterList &&
        reference.getLastChild() == positionElement) {
        final PsiElement statement = reference.getParent();
        if (!(statement instanceof PsiExpressionStatement)) return null;

        // todo: will it handle 'a instanceof T.if' - ES;Error;ES;?
        PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(statement, PsiStatement.class);
        if (!(prevStatement instanceof PsiExpressionStatement)) return null;

        PsiElement lastErrorChild = prevStatement.getLastChild();
        if (lastErrorChild instanceof PsiErrorElement) {
          PsiExpression expression = ((PsiExpressionStatement) prevStatement).getExpression();
          if (prevStatement.getFirstChild() == expression &&
            lastErrorChild.getPrevSibling() == expression) {

            final PsiLiteralExpression brokenLiteral = findBrokenLiteral(expression);
            if (brokenLiteral != null) {
              return new PostfixTemplateAcceptanceContext(reference, brokenLiteral, forceMode) {
                @Override @NotNull public PrefixExpressionContext fixUpExpression(
                    @NotNull PrefixExpressionContext context) {
                  statement.delete(); // remove extra statement

                  // fix broken double literal by cutting of "." suffix
                  Project project = context.expression.getProject();
                  PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                  String literalText = brokenLiteral.getText();
                  String fixedText = literalText.substring(0, literalText.length() - 1);
                  PsiLiteralExpression fixedLiteral = (PsiLiteralExpression)
                    factory.createExpressionFromText(fixedText, null);

                  brokenLiteral.replace(fixedLiteral);
                  return context;
                }
              };
            }
          }
        }
      }
    } else if (parent instanceof PsiJavaCodeReferenceElement) {
      // TODO: prettify
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement) parent).getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return null;
      }

      // handle 'foo instanceof Bar.postfix' expressions
      PsiElement referenceParent = parent.getParent();
      if (referenceParent instanceof PsiTypeElement) {
        PsiElement expression = referenceParent.getParent();
        if (expression instanceof PsiInstanceOfExpression) {
          PsiExpression instanceOfExpression = (PsiInstanceOfExpression) expression;
          return new PostfixTemplateAcceptanceContext(parent, instanceOfExpression, forceMode) {
            @NotNull @Override public PrefixExpressionContext
                fixUpExpression(@NotNull PrefixExpressionContext context) {

              parent.replace(qualifier);
              assert context.expression.isValid(); // ?

              return context;
            }
          };
        }
      }
    }

    return null;
  }

  @Nullable private PsiLiteralExpression findBrokenLiteral(@NotNull PsiExpression expr) {
    PsiExpression expression = expr;
    do {
      // look for double literal broken by dot at end
      if (expression instanceof PsiLiteralExpression) {
        PsiJavaToken token = PsiTreeUtil.getChildOfType(expression, PsiJavaToken.class);
        if (token != null
            && token.getTokenType() == JavaTokenType.DOUBLE_LITERAL
            && token.getText().endsWith("."))
          return (PsiLiteralExpression) expression;
      }

      // skip current expression and look its last inner expression
      PsiElement last = expression.getLastChild();
      if (last instanceof PsiExpression) expression = (PsiExpression) last;
      else expression = PsiTreeUtil.getPrevSiblingOfType(last, PsiExpression.class);
    } while (expression != null);

    return null;
  }

  @NotNull public List<LookupElement> collectTemplates(@NotNull PostfixTemplateAcceptanceContext context) {
    List<LookupElement> elements = new ArrayList<LookupElement>();

    for (TemplateProviderInfo providerInfo : myProviders)
      providerInfo.provider.createItems(context, elements);

    return elements;
  }

  @Override public void initComponent() { }
  @Override public void disposeComponent() { }

  @NotNull @Override public String getComponentName() {
    return PostfixTemplatesManager.class.getName();
  }
}