package org.jetbrains.postfixCompletion.Infrastructure;

import com.intellij.codeInsight.lookup.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.*;

import java.util.*;

// todo: check usages of PsiBinaryExpression with PsiPolyadicExpression (2 + 2 + 2)
// todo: support '2 + 2 .var' (with spacing)

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
      final PsiReferenceExpression reference = (PsiReferenceExpression) parent;

      // easy case: 'expr.postfix'
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null) {
        return new PostfixTemplateAcceptanceContext(reference, qualifier, forceMode) {
          @Override @NotNull public PrefixExpressionContext fixUpExpression(@NotNull PrefixExpressionContext context) {
            PsiExpression expression = context.expression;
            PsiReferenceExpression referenceExpression = (PsiReferenceExpression) postfixReference;

            // replace 'expr.postfix' with 'expr'
            if (expression.getParent() == referenceExpression) {
              PsiExpression newExpression = (PsiExpression) referenceExpression.replace(expression);
              return new PrefixExpressionContext(this, newExpression);
            }

            // replace '0 > expr.postfix' with '0 > expr'
            if (PsiTreeUtil.findCommonParent(referenceExpression, expression) == expression) {
              PsiElement expr = referenceExpression.getQualifier();
              assert expr != null : "expr != null";

              referenceExpression.replace(expr);
              return context; // yes, the same context
            }

            return context;
          }
        };
      }

      // cases like 'x > 0.if' and '2.var + 2' (two expression-statement and broken literal)
      if (reference.getFirstChild() instanceof PsiReferenceParameterList &&
          reference.getLastChild() == positionElement) {
        // find enclosing expression-statement through expressions
        final PsiExpressionStatement exprStatement = findContainingExprStatement(reference.getParent());

        // TODO: 2 + 2.var + 2 + 2

        if (exprStatement != null) {
          final PsiStatement prevStatement = PsiTreeUtil.getPrevSiblingOfType(exprStatement, PsiStatement.class);
          final PsiExpression expression = findUnfinishedExpression(prevStatement);
          if (expression != null) {
            final PsiLiteralExpression brokenLiteral = findBrokenLiteral(expression);
            if (brokenLiteral != null) {
              return new PostfixTemplateAcceptanceContext(reference, brokenLiteral, forceMode) {
                @Override @NotNull public PrefixExpressionContext fixUpExpression(
                    @NotNull PrefixExpressionContext context) {
                  // fix broken double literal by cutting of "." suffix
                  Project project = context.expression.getProject();
                  String literalText = brokenLiteral.getText();
                  String fixedText = literalText.substring(0, literalText.length() - 1);
                  PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
                  PsiLiteralExpression newLiteral = (PsiLiteralExpression)
                    factory.createExpressionFromText(fixedText, null);

                  PsiExpression newExpression, oldExpression;
                  if (expression == brokenLiteral) {
                    oldExpression = brokenLiteral;
                    newExpression = (PsiExpression) reference.replace(newLiteral);
                  } else {
                    brokenLiteral.replace(newLiteral);
                    oldExpression = expression;
                    newExpression = (PsiExpression) reference.replace(expression.copy());
                  }

                  assert newExpression.isPhysical() : "newExpression.isPhysical()";
                  assert oldExpression.isPhysical() : "oldExpression.isPhysical()";

                  PsiExpressionStatement statement = findContainingExprStatement(newExpression.getParent());
                  if (statement != null) {
                    newExpression.putCopyableUserData(marker, marker);

                    PsiExpression outerExpression = statement.getExpression();
                    newExpression = (PsiExpression) oldExpression.replace(outerExpression);

                    PsiExpression marked = findMarkedExpression(newExpression);
                    if (marked != null) newExpression = marked;
                  } else {
                    newExpression = (PsiExpression) oldExpression.replace(newExpression);
                  }

                  exprStatement.delete();
                  return new PrefixExpressionContext(this, newExpression);
                }

                @Override public boolean isBrokenStatement(@NotNull PsiStatement statement) {
                  return statement == prevStatement;
                }
              };
            }
          }
        }
      }
    } else if (parent instanceof PsiJavaCodeReferenceElement) {
      // check for qualifier of '.postfix' parsed as code-reference-element
      final PsiElement qualifier = ((PsiJavaCodeReferenceElement) parent).getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) return null;

      PsiElement referenceParent = parent.getParent();
      if (referenceParent instanceof PsiTypeElement) {
        final PsiElement psiElement = referenceParent.getParent();
        // handle 'foo instanceof Bar.postfix' expressions
        // todo: wrong? skips 'Bar' as a type
        if (psiElement instanceof PsiInstanceOfExpression) {
          PsiExpression instanceOfExpression = (PsiInstanceOfExpression) psiElement;
          return new PostfixTemplateAcceptanceContext(parent, instanceOfExpression, forceMode) {
            @NotNull @Override public PrefixExpressionContext
                fixUpExpression(@NotNull PrefixExpressionContext context) {
              parent.replace(qualifier);
              assert context.expression.isValid();
              return context;
            }
          };
        }

        // handle 'Bar<T>.postfix' type expressions (parsed as declaration-statement)
        if (psiElement instanceof PsiDeclarationStatement &&
            psiElement.getLastChild() instanceof PsiErrorElement && // only simple
            psiElement.getFirstChild() == referenceParent) {
          // copy and fix type usage from '.postfix' suffix
          PsiTypeElement refParentCopy = (PsiTypeElement) referenceParent.copy();
          PsiJavaCodeReferenceElement referenceElement = refParentCopy.getInnermostComponentReferenceElement();
          assert referenceElement != null : "referenceElement != null";

          PsiElement referenceQualifier = referenceElement.getQualifier();
          assert referenceQualifier != null : "referenceQualifier != null";
          referenceElement.replace(referenceQualifier); // remove '.postfix'

          // reinterpret type usages as 'new T();' expression-statement
          PsiElementFactory factory = JavaPsiFacade.getElementFactory(psiElement.getProject());
          final PsiExpressionStatement statement = (PsiExpressionStatement)
            factory.createStatementFromText("new " + refParentCopy.getText() + "()", psiElement);

          // todo: somehow mark that this is not real new-expression
          return new PostfixTemplateAcceptanceContext(parent, statement.getExpression(), forceMode) {
            @NotNull @Override public PrefixExpressionContext
                fixUpExpression(@NotNull PrefixExpressionContext context) {
              PsiExpressionStatement newStatement = (PsiExpressionStatement) psiElement.replace(statement);
              return new PrefixExpressionContext(this, newStatement.getExpression());
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

  @Nullable private static PsiExpression findUnfinishedExpression(@NotNull PsiStatement statement) {
    PsiElement lastChild = statement.getLastChild();
    if (statement instanceof PsiExpressionStatement) {
      if (lastChild instanceof PsiErrorElement && lastChild.getPrevSibling() instanceof PsiExpression) {
        return ((PsiExpressionStatement) statement).getExpression();
      }
    } else if (statement instanceof PsiDeclarationStatement) {
      if (lastChild instanceof PsiLocalVariable && lastChild.getLastChild() instanceof PsiErrorElement) {
        return ((PsiLocalVariable) lastChild).getInitializer();
      }
    }

    return null;
  }

  @Nullable private static PsiExpressionStatement findContainingExprStatement(@NotNull PsiElement element) {
    while (element instanceof PsiBinaryExpression ||
           element instanceof PsiPolyadicExpression) {
      element = element.getParent();
    }

    if (element instanceof PsiExpressionStatement) {
      return (PsiExpressionStatement) element;
    }

    return null;
  }

  @Nullable private static PsiExpression findMarkedExpression(@NotNull PsiExpression expression) {
    if (expression.getCopyableUserData(marker) != null) return expression;

    for (PsiElement node = expression.getFirstChild(); node != null; node = node.getNextSibling()) {
      if (node instanceof PsiExpression) {
        PsiExpression markedExpression = findMarkedExpression((PsiExpression) node);
        if (markedExpression != null) return markedExpression;
      }
    }

    return null;
  }

  static final com.intellij.openapi.util.Key marker = new Key(PostfixTemplatesManager.class.getName());

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