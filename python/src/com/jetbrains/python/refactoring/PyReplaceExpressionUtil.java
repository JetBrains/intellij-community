package com.jetbrains.python.refactoring;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.PyTokenTypes.*;

/**
 * @author Dennis.Ushakov
 */
public class PyReplaceExpressionUtil implements PyElementTypes {
  public static final Key<Pair<PsiElement, TextRange>> SELECTION_BREAKS_AST_NODE =
    new Key<Pair<PsiElement, TextRange>>("python.selection.breaks.ast.node");

  private PyReplaceExpressionUtil() {}

  public static boolean isNeedParenthesis(@NotNull final PyElement oldExpr, @NotNull final PyElement newExpr) {
    final PyElement parentExpr = (PyElement)oldExpr.getParent();
    if (parentExpr instanceof PyArgumentList) {
      return newExpr instanceof PyTupleExpression;
    }
    if (!(parentExpr instanceof PyExpression)) {
      return false;
    }
    int newPriority = getExpressionPriority(newExpr);
    int parentPriority = getExpressionPriority(parentExpr);
    if (parentPriority > newPriority) {
      return true;
    } else if (parentPriority == newPriority && parentPriority != 0) {
      if (parentExpr instanceof PyBinaryExpression) {
        PyBinaryExpression binaryExpression = (PyBinaryExpression)parentExpr;
        if (isNotAssociative(binaryExpression) && oldExpr.equals(binaryExpression.getRightExpression())) {
          return true;
        }
      }
    }
    return false;
  }

  public static PsiElement replaceExpression(@NotNull final PsiElement oldExpression,
                                             @NotNull final PsiElement newExpression) {
    final Pair<PsiElement, TextRange> data = oldExpression.getUserData(SELECTION_BREAKS_AST_NODE);
    if (data != null) {
      final PsiElement element = data.first;
      final TextRange textRange = data.second;
      final String parentText = element.getText();
      final String prefix = parentText.substring(0, textRange.getStartOffset());
      final String suffix = parentText.substring(textRange.getEndOffset(), element.getTextLength());
      final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
      final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
      if (element instanceof PyStringLiteralExpression) {
        return replaceSubstringInStringLiteral((PyStringLiteralExpression)element, newExpression, textRange);
      }
      final PsiElement expression = generator.createFromText(languageLevel, element.getClass(), prefix + newExpression.getText() + suffix);
      return element.replace(expression);
    }
    else {
      return oldExpression.replace(newExpression);
    }
  }

  @Nullable
  private static PsiElement replaceSubstringInStringLiteral(@NotNull PyStringLiteralExpression oldExpression,
                                                            @NotNull PsiElement newExpression,
                                                            @NotNull TextRange textRange) {
    final String fullText = oldExpression.getText();
    final PyElementGenerator generator = PyElementGenerator.getInstance(oldExpression.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(oldExpression);
    final String prefix = fullText.substring(0, textRange.getStartOffset());
    final String suffix = fullText.substring(textRange.getEndOffset(), oldExpression.getTextLength());
    final Pair<String, String> detectedQuotes = PythonStringUtil.getQuotes(fullText);
    final Pair<String, String> quotes = detectedQuotes != null ? detectedQuotes : Pair.create("'", "'");
    final PsiElement parent = oldExpression.getParent();
    final boolean parensNeeded = parent instanceof PyExpression && !(parent instanceof PyParenthesizedExpression);
    final String leftQuote = quotes.getFirst();
    final String rightQuote = quotes.getSecond();
    final StringBuilder builder = new StringBuilder();
    if (parensNeeded) {
      builder.append("(");
    }
    if (!leftQuote.endsWith(prefix)) {
      builder.append(prefix + rightQuote + " + ");
    }
    final int pos = builder.toString().length();
    builder.append(newExpression.getText());
    if (!rightQuote.startsWith(suffix)) {
      builder.append(" + " + leftQuote + suffix);
    }
    if (parensNeeded) {
      builder.append(")");
    }
    final PsiElement expression = generator.createExpressionFromText(languageLevel, builder.toString());
    final PsiElement newElement = oldExpression.replace(expression);
    return newElement.findElementAt(pos);
  }

  private static boolean isNotAssociative(@NotNull final PyBinaryExpression binaryExpression) {
    final IElementType opType = getOperationType(binaryExpression);
    return COMPARISON_OPERATIONS.contains(opType) || binaryExpression instanceof PySliceExpression ||
           opType == DIV || opType == PERC || opType == EXP;
  }

  private static int getExpressionPriority(PyElement expr) {
    int priority = 0;
    if (expr instanceof PySubscriptionExpression || expr instanceof PySliceExpression ||
        expr instanceof PyCallExpression) priority = 1;
    if (expr instanceof PyPrefixExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == PLUS || opType == MINUS || opType == TILDE) priority = 2;
      if (opType == NOT_KEYWORD) priority = 10;
    }
    if (expr instanceof PyBinaryExpression) {
      final IElementType opType = getOperationType(expr);
      if (opType == EXP) priority =  3;
      if (opType == MULT || opType == DIV || opType == PERC) priority =  4;
      if (opType == PLUS || opType == MINUS) priority =  5;
      if (opType == LTLT || opType == GTGT) priority = 6;
      if (opType == AND) priority = 7;
      if (opType == OR) priority = 8;
      if (COMPARISON_OPERATIONS.contains(opType)) priority = 9;
      if (opType == AND_KEYWORD) priority = 11;
    }
    if (expr instanceof PyLambdaExpression) priority = 12; 

    return -priority;
  }

  @NotNull
  private static IElementType getOperationType(@NotNull final PyElement expr) {
    if (expr instanceof PyBinaryExpression) return ((PyBinaryExpression)expr).getOperator();
    return ((PyPrefixExpression)expr).getOperator();
  }
}
