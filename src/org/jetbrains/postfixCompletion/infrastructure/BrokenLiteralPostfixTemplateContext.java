package org.jetbrains.postfixCompletion.infrastructure;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class BrokenLiteralPostfixTemplateContext extends PostfixTemplateContext {
  @NotNull private final PsiLiteralExpression myBrokenLiteral;
  @NotNull private final PsiExpression myLhsExpression;
  @NotNull private final PsiStatement myLhsStatement;

  public BrokenLiteralPostfixTemplateContext(@NotNull PsiReferenceExpression reference,
                                             @NotNull PsiLiteralExpression brokenLiteral,
                                             @NotNull PsiExpression lhsExpression,
                                             @NotNull PsiStatement lhsStatement,
                                             @NotNull PostfixExecutionContext executionContext) {
    super(reference, brokenLiteral, executionContext);
    myBrokenLiteral = brokenLiteral;
    myLhsExpression = lhsExpression;
    myLhsStatement = lhsStatement;
  }

  @Override
  @NotNull
  public PrefixExpressionContext fixExpression(@NotNull PrefixExpressionContext context) {
    Project project = context.expression.getProject();

    // store pointer to rhs expression all the time
    SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
    SmartPsiElementPointer<PsiExpression> lhsExpressionPointer =
      pointerManager.createSmartPsiElementPointer(myLhsExpression);

    // fix literal at PSI-level first
    String brokenLiteralText = myBrokenLiteral.getText();
    int dotIndex = brokenLiteralText.lastIndexOf('.');
    assert dotIndex > 0;

    String fixedLiteralText = brokenLiteralText.substring(0, dotIndex);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiExpression fixedLiteral = factory.createExpressionFromText(fixedLiteralText, null);
    fixedLiteral = (PsiExpression)myBrokenLiteral.replace(fixedLiteral);

    // now let's fix broken statements at document-level
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    Document document = documentManager.getDocument(fixedLiteral.getContainingFile());
    assert document != null;

    documentManager.doPostponedOperationsAndUnblockDocument(document);

    PsiExpression lhsExpression = lhsExpressionPointer.getElement();
    assert lhsExpression != null;

    // calculate ranges to modify
    int literalStart = fixedLiteral.getTextRange().getEndOffset();
    int referenceEnd = context.parentContext.postfixReference.getTextRange().getEndOffset();
    int exprStart = lhsExpression.getTextRange().getStartOffset();

    document.replaceString(literalStart, referenceEnd, ")");
    document.replaceString(exprStart, exprStart, "(");

    documentManager.commitDocument(document);

    // let's find restored expressions in fresh PSI
    lhsExpression = lhsExpressionPointer.getElement();
    assert lhsExpression != null;

    // pointer may resolve in more outer expression, try to correct
    PsiElement leftPar = lhsExpression.getContainingFile().findElementAt(exprStart /* "(" now */);
    if (leftPar != null && leftPar instanceof PsiJavaToken &&
        leftPar.getParent() instanceof PsiParenthesizedExpression) {
      PsiParenthesizedExpression parenthesized = (PsiParenthesizedExpression)leftPar.getParent();
      PsiExpression expression = parenthesized.getExpression();
      if (expression != null) {
        lhsExpression = (PsiExpression)parenthesized.replace(expression);
      }
    }

    return new PrefixExpressionContext(context.parentContext, lhsExpression);
  }

  @NotNull
  @Override
  protected PrefixExpressionContext buildExpressionContext(@NotNull PsiElement expression) {
    if (expression == myBrokenLiteral) {
      return new PrefixExpressionContext(this, expression) {
        @Nullable
        @Override
        protected PsiType calculateExpressionType(@NotNull PsiElement expression) {
          return PsiType.INT; // note: yeah, can't be long
        }
      };
    }

    // broken literal can change type of containing expression(s)
    if (expression instanceof PsiExpression) {
      PsiElement[] children = expression.getChildren();
      int literalIndex = Arrays.asList(children).indexOf(myBrokenLiteral);
      if (literalIndex >= 0) { // check one level up
        PsiExpression expressionCopy = (PsiExpression)expression.copy();
        PsiLiteralExpression brokenCopy = (PsiLiteralExpression)expressionCopy.getChildren()[literalIndex];
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(postfixReference.getProject());
        PsiLiteralExpression normalLiteral =
          (PsiLiteralExpression)factory.createExpressionFromText("1", postfixReference);

        brokenCopy.replace(normalLiteral);
        final PsiType fixedType = expressionCopy.getType();
        if (fixedType != null) {
          return new PrefixExpressionContext(this, expression) {
            @Nullable
            @Override
            protected PsiType calculateExpressionType(@NotNull PsiElement expression) {
              return fixedType;
            }
          };
        }
      }
    }

    return super.buildExpressionContext(expression);
  }

  @Nullable
  @Override
  public PsiStatement getContainingStatement(@NotNull PrefixExpressionContext context) {
    PsiStatement statement = super.getContainingStatement(context);

    // ignore expression-statements produced by broken expr like '2.var + 2'
    // note: only when lhsStatement is not 'fixed' yet
    if (statement != null && myLhsStatement.isValid()) {
      boolean isComplexRhs = !(postfixReference.getParent() instanceof PsiExpressionStatement);
      if (isComplexRhs && (statement == myLhsStatement)) return null;
    }

    return statement;
  }

  @Nullable
  @Override
  public String shouldFixPrefixMatcher() {
    String brokenLiteralText = myBrokenLiteral.getText();
    int dotIndex = brokenLiteralText.lastIndexOf('.');
    if (dotIndex < brokenLiteralText.length()) {
      return brokenLiteralText.substring(dotIndex + 1);
    }

    return super.shouldFixPrefixMatcher();
  }

  @Nullable
  public static PsiLiteralExpression findBrokenLiteral(@Nullable PsiExpression expr) {
    if (expr == null) return null;

    PsiExpression expression = expr;
    do {
      // look for double literal broken by dot at end
      if (expression instanceof PsiLiteralExpression) {
        PsiJavaToken token = PsiTreeUtil.getChildOfType(expression, PsiJavaToken.class);
        if (token != null) {
          IElementType tokenType = token.getTokenType();
          if (tokenType == JavaTokenType.DOUBLE_LITERAL || tokenType == JavaTokenType.FLOAT_LITERAL) {
            if (token.getText().matches("^.*?\\.\\D*$")) { // omfg
              return (PsiLiteralExpression)expression;
            }
          }
        }
      }

      // skip current expression and look its last inner expression
      PsiElement last = expression.getLastChild();
      if (last instanceof PsiExpression) {
        expression = (PsiExpression)last;
      }
      else {
        expression = PsiTreeUtil.getPrevSiblingOfType(last, PsiExpression.class);
      }
    }
    while (expression != null);

    return null;
  }

  @Nullable
  public static PsiExpression findUnfinishedExpression(@Nullable PsiStatement statement) {
    if (statement == null) return null;

    PsiElement lastChild = statement.getLastChild();
    while (lastChild != null) {
      if (lastChild instanceof PsiErrorElement && lastChild.getPrevSibling() instanceof PsiExpression) {
        return (PsiExpression)lastChild.getPrevSibling();
      }

      lastChild = lastChild.getLastChild();
    }

    return null;
  }
}