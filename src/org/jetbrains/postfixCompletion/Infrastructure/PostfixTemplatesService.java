package org.jetbrains.postfixCompletion.infrastructure;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.impl.editorActions.ExpandLiveTemplateByTabAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.ExpandPostfixEditorActionHandler;
import org.jetbrains.postfixCompletion.settings.PostfixCompletionSettings;
import org.jetbrains.postfixCompletion.templates.PostfixTemplateProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// todo: support '2 + 2 .var' (with spacing)
/* todo: foo.bar
 *       123.fori    */

public final class PostfixTemplatesService {
  @NotNull private final List<TemplateProviderInfo> myProviders;

  public PostfixTemplatesService() {
    List<TemplateProviderInfo> providerInfos = new ArrayList<TemplateProviderInfo>();
    for (PostfixTemplateProvider provider : PostfixTemplateProvider.EP_NAME.getExtensions()) {
      TemplateProvider annotation = provider.getClass().getAnnotation(TemplateProvider.class);
      if (annotation != null) {
        providerInfos.add(new TemplateProviderInfo(provider, annotation));
      }
    }

    myProviders = Collections.unmodifiableList(providerInfos);

    // wrap 'ExpandLiveTemplateByTab' action handler
    // todo: configurable?
    ActionManager actionManager = ActionManager.getInstance();
    AnAction expandLiveTemplate = actionManager.getAction("ExpandLiveTemplateByTab");
    if (expandLiveTemplate instanceof ExpandLiveTemplateByTabAction) {
      EditorAction expandAction = (EditorAction) expandLiveTemplate;

      EditorActionHandler existingHandler = expandAction.getHandler(); // hack :(
      expandAction.setupHandler(new ExpandPostfixEditorActionHandler(existingHandler, this));
    }
  }

  @Nullable
  public static PostfixTemplatesService getInstance() {
    return ServiceManager.getService(PostfixTemplatesService.class);
  }

  @NotNull public final List<TemplateProviderInfo> getAllTemplates() {
    return myProviders;
  }

  @Nullable public final PostfixTemplateContext isAvailable(
      @NotNull PsiElement positionElement, @NotNull PostfixExecutionContext executionContext) {

    // postfix name always is identifier
    if (!(positionElement instanceof PsiIdentifier)) return null;

    final PsiElement parent = positionElement.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiReferenceExpression reference = (PsiReferenceExpression) parent;

      // easy case: 'expr.postfix'
      PsiExpression qualifier = reference.getQualifierExpression();
      if (qualifier != null) {
        return new PostfixTemplateContext(reference, qualifier, executionContext) {
          @Override @NotNull
          public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
            PsiExpression expression = (PsiExpression) context.expression;
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
        if (exprStatement != null) {
          PsiStatement lhsStatement = PsiTreeUtil.getPrevSiblingOfType(exprStatement, PsiStatement.class);
          PsiExpression lhsExpression = BrokenLiteralPostfixTemplateContext.findUnfinishedExpression(lhsStatement);
          PsiLiteralExpression brokenLiteral = BrokenLiteralPostfixTemplateContext.findBrokenLiteral(lhsExpression);
          if (lhsExpression != null && brokenLiteral != null) {
            return new BrokenLiteralPostfixTemplateContext(
              reference, brokenLiteral, lhsExpression,
              lhsStatement, executionContext);
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

        // handle 'foo instanceof Bar.postfix' expressions (not 'Bar' type itself)
        if (psiElement instanceof PsiInstanceOfExpression) {
          PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement) parent;
          PsiExpression instanceOfExpression = (PsiInstanceOfExpression) psiElement;

          return new PostfixTemplateContext(reference, instanceOfExpression, executionContext) {
            @NotNull @Override
            public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
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
          final PsiJavaCodeReferenceElement reference = (PsiJavaCodeReferenceElement) parent;
          return new PostfixTemplateContext(reference, qualifier, executionContext) {
            @NotNull @Override
            public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
              // note: sometimes it can be required to use document-level fix
              // todo: test .new with various statements as next statements in block

              PsiElement fixedReference = reference.replace(context.expression);
              return new PrefixExpressionContext(this, fixedReference);
            }

            @Nullable @Override
            public PsiStatement getContainingStatement(@NotNull PrefixExpressionContext context) {
              // note: not always correct?
              if (context.expression instanceof PsiJavaCodeReferenceElement) {
                PsiElement parent = context.expression.getParent();
                if (parent instanceof PsiJavaCodeReferenceElement && parent == reference) {
                  parent = parent.getParent();
                }

                if (parent instanceof PsiTypeElement) {
                  PsiElement typeElementOwner = parent.getParent();
                  if (typeElementOwner instanceof PsiDeclarationStatement) {
                    return (PsiStatement) typeElementOwner;
                  }
                }
              }

              return super.getContainingStatement(context);
            }
          };
        }
      }
    }

    return null;
  }

  @Nullable private static PsiExpressionStatement findContainingExprStatement(@NotNull PsiElement element) {
    while (element instanceof PsiBinaryExpression ||
           element instanceof PsiPolyadicExpression) {
      // todo: check with postfix/prefix expressions
      element = element.getParent();
    }

    if (element instanceof PsiExpressionStatement) {
      return (PsiExpressionStatement) element;
    }

    return null;
  }

  @NotNull public List<LookupElement> collectTemplates(@NotNull PostfixTemplateContext context) {
    final PostfixCompletionSettings settings = PostfixCompletionSettings.getInstance();
    if (settings == null) {
      return Collections.emptyList();
    }
    
    // disable all providers over package names
    PsiElement referencedElement = context.innerExpression().referencedElement;
    if (referencedElement instanceof PsiPackage) return Collections.emptyList();

    // check we invoked on type
    boolean invokedOnType = (referencedElement instanceof PsiClass);
    boolean insideCodeFragment = context.executionContext.insideCodeFragment;
    List<LookupElement> elements = new ArrayList<LookupElement>();


    for (TemplateProviderInfo providerInfo : myProviders) {
      if (invokedOnType && !providerInfo.annotation.worksOnTypes()) continue;
      if (insideCodeFragment && !providerInfo.annotation.worksInsideFragments()) continue;
      try {
        if (settings.isTemplateEnabled(providerInfo)) {
          providerInfo.provider.createItems(context, elements);
        }
      } catch (Exception ex) {
        LOG.error(ex);
      }
    }

    return elements;
  }

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.postfixCompletion");
}