package com.jetbrains.python.ast.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assorted utility methods for Python code insight.
 *
 * These methods don't depend on the Python runtime.
 *
 * @see PyPsiUtilsCore for utilities used in Python PSI API
 */
@ApiStatus.Experimental
public final class PyUtilCore {

  private static final Pattern TYPE_COMMENT_PATTERN = Pattern.compile("# *type: *([^#]+) *(#.*)?");

  private PyUtilCore() {
  }

  /**
   * @see PyUtil#flattenedParensAndTuples
   */
  private static List<PyAstExpression> unfoldParentheses(PyAstExpression[] targets, List<PyAstExpression> receiver,
                                                         boolean unfoldListLiterals, boolean unfoldStarExpressions) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (PyAstExpression exp : targets) {
      if (exp instanceof PyAstParenthesizedExpression parenExpr) {
        unfoldParentheses(new PyAstExpression[]{parenExpr.getContainedExpression()}, receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstTupleExpression tupleExpr) {
        unfoldParentheses(tupleExpr.getElements(), receiver, unfoldListLiterals, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstListLiteralExpression listLiteral && unfoldListLiterals) {
        unfoldParentheses(listLiteral.getElements(), receiver, true, unfoldStarExpressions);
      }
      else if (exp instanceof PyAstStarExpression && unfoldStarExpressions) {
        unfoldParentheses(new PyAstExpression[]{((PyAstStarExpression)exp).getExpression()}, receiver, unfoldListLiterals, true);
      }
      else if (exp != null) {
        receiver.add(exp);
      }
    }
    return receiver;
  }

  /**
   * Flattens the representation of every element in targets, and puts all results together.
   * Elements of every tuple nested in target item are brought to the top level: (a, (b, (c, d))) -> (a, b, c, d)
   * Typical usage: {@code flattenedParensAndTuples(some_tuple.getExpressions())}.
   *
   * @param targets target elements.
   * @return the list of flattened expressions.
   */
  @NotNull
  public static List<PyAstExpression> flattenedParensAndTuples(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, false);
  }

  @NotNull
  public static List<PyAstExpression> flattenedParensAndLists(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), true, true);
  }

  @NotNull
  public static List<PyAstExpression> flattenedParensAndStars(PyAstExpression... targets) {
    return unfoldParentheses(targets, new ArrayList<>(targets.length), false, true);
  }

  public static boolean onSameLine(@NotNull PsiElement e1, @NotNull PsiElement e2) {
    PsiFile firstFile = e1.getContainingFile();
    PsiFile secondFile = e2.getContainingFile();
    if (firstFile == null || secondFile == null) return false;
    Document document = firstFile.getFileDocument();
    if (document != secondFile.getFileDocument()) return false;
    return document.getLineNumber(e1.getTextOffset()) == document.getLineNumber(e2.getTextOffset());
  }

  public static boolean isTopLevel(@NotNull PsiElement element) {
    if (element instanceof StubBasedPsiElement) {
      final StubElement stub = ((StubBasedPsiElement<?>)element).getStub();
      if (stub != null) {
        final StubElement parentStub = stub.getParentStub();
        if (parentStub != null) {
          return parentStub.getPsi() instanceof PsiFile;
        }
      }
    }
    return ScopeUtilCore.getScopeOwner(element) instanceof PsiFile;
  }

  /**
   * Retrieves the document from {@link PsiDocumentManager} using the anchor PSI element and, if it's not null,
   * passes it to the consumer function.
   * <p>
   * The document is first released from pending PSI operations and then committed after the function has been applied
   * in a {@code try/finally} block, so that subsequent operations on PSI could be performed.
   *
   * @see PsiDocumentManager#doPostponedOperationsAndUnblockDocument(Document)
   * @see PsiDocumentManager#commitDocument(Document)
   * @see #updateDocumentUnblockedAndCommitted(PsiElement, Function)
   */
  public static void updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Consumer<? super Document> consumer) {
    updateDocumentUnblockedAndCommitted(anchor, document -> {
      consumer.consume(document);
      return null;
    });
  }

  @Nullable
  public static <T> T updateDocumentUnblockedAndCommitted(@NotNull PsiElement anchor, @NotNull Function<? super Document, ? extends T> func) {
    final PsiDocumentManager manager = PsiDocumentManager.getInstance(anchor.getProject());
    // manager.getDocument(anchor.getContainingFile()) doesn't work with intention preview
    final Document document = anchor.getContainingFile().getViewProvider().getDocument();
    if (document != null) {
      manager.doPostponedOperationsAndUnblockDocument(document);
      try {
        return func.fun(document);
      }
      finally {
        manager.commitDocument(document);
      }
    }
    return null;
  }

  public static boolean isSpecialName(@NotNull String name) {
    return name.length() > 4 && name.startsWith("__") && name.endsWith("__");
  }

  @Nullable
  public static PyAstLoopStatement getCorrespondingLoop(@NotNull PsiElement breakOrContinue) {
    return breakOrContinue instanceof PyAstContinueStatement || breakOrContinue instanceof PyAstBreakStatement
           ? getCorrespondingLoopImpl(breakOrContinue)
           : null;
  }

  @Nullable
  private static PyAstLoopStatement getCorrespondingLoopImpl(@NotNull PsiElement element) {
    final PyAstLoopStatement loop = PsiTreeUtil.getParentOfType(element, PyAstLoopStatement.class, true, AstScopeOwner.class);

    if (loop instanceof PyAstStatementWithElse && PsiTreeUtil.isAncestor(((PyAstStatementWithElse)loop).getElsePart(), element, true)) {
      return getCorrespondingLoopImpl(loop);
    }

    return loop;
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isInitOrNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isNewMethod(@Nullable PsiElement element) {
    final PyAstFunction function = ObjectUtils.tryCast(element, PyAstFunction.class);
    return function != null && PyNames.NEW.equals(function.getName()) && function.getContainingClass() != null;
  }

  /**
   * @return true if passed {@code element} is a method (this means a function inside a class) named {@code __init__} or {@code __new__}.
   * @see PyUtil#isInitMethod(PsiElement)
   * @see PyUtil#isNewMethod(PsiElement)
   * @see PyUtil#turnConstructorIntoClass(PyFunction)
   */
  @Contract("null -> false")
  public static boolean isInitOrNewMethod(@Nullable PsiElement element) {
    final PyAstFunction function = ObjectUtils.tryCast(element, PyAstFunction.class);
    if (function == null) return false;

    final String name = function.getName();
    return (PyNames.INIT.equals(name) || PyNames.NEW.equals(name)) && function.getContainingClass() != null;
  }

  public static boolean isStringLiteral(@Nullable PyAstStatement stmt) {
    if (stmt instanceof PyAstExpressionStatement) {
      final PyAstExpression expr = ((PyAstExpressionStatement)stmt).getExpression();
      if (expr instanceof PyAstStringLiteralExpression) {
        return true;
      }
    }
    return false;
  }

  /**
   * Counts initial underscores of an identifier.
   *
   * @param name identifier
   * @return 0 if null or no initial underscores found, 1 if there's only one underscore, 2 if there's two or more initial underscores.
   */
  public static int getInitialUnderscores(@Nullable String name) {
    return name == null ? 0 : name.startsWith("__") ? 2 : name.startsWith(PyNames.UNDERSCORE) ? 1 : 0;
  }

  @Nullable
  public static List<String> strListValue(PyAstExpression value) {
    while (value instanceof PyAstParenthesizedExpression) {
      value = ((PyAstParenthesizedExpression)value).getContainedExpression();
    }
    if (value instanceof PyAstSequenceExpression) {
      final PyAstExpression[] elements = ((PyAstSequenceExpression)value).getElements();
      List<String> result = new ArrayList<>(elements.length);
      for (PyAstExpression element : elements) {
        if (!(element instanceof PyAstStringLiteralExpression)) {
          return null;
        }
        result.add(((PyAstStringLiteralExpression)element).getStringValue());
      }
      return result;
    }
    return null;
  }

  public static boolean isAssignmentToModuleLevelDunderName(@Nullable PsiElement element) {
    if (element instanceof PyAstAssignmentStatement statement && isTopLevel(element)) {
      PyAstExpression[] targets = statement.getTargets();
      if (targets.length == 1) {
        String name = targets[0].getName();
        return name != null && isSpecialName(name);
      }
    }
    return false;
  }

  /**
   * Returns the line comment that immediately precedes statement list of the given compound statement. Python parser ensures
   * that it follows the statement header, i.e. it's directly after the colon, not on its own line.
   */
  @Nullable
  public static PsiComment getCommentOnHeaderLine(@NotNull PyAstStatementListContainer container) {
    return ObjectUtils.tryCast(getHeaderEndAnchor(container), PsiComment.class);
  }

  @NotNull
  public static PsiElement getHeaderEndAnchor(@NotNull PyAstStatementListContainer container) {
    final PyAstStatementList statementList = container.getStatementList();
    return Objects.requireNonNull(PsiTreeUtil.skipWhitespacesBackward(statementList));
  }

  /**
   * Checks that text of a comment starts with "# type:" prefix and returns trimmed type hint after it.
   * The trailing part is supposed to contain type annotation in PEP 484 compatible format and an optional
   * plain text comment separated from it with another "#".
   * <p>
   * For instance, for {@code # type: List[int]  # comment} it returns {@code List[int]}.
   * <p>
   * This method cannot return an empty string.
   *
   * @see #getTypeCommentValueRange(String)
   */
  @Nullable
  public static String getTypeCommentValue(@NotNull String text) {
    final Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      return StringUtil.nullize(m.group(1).trim());
    }
    return null;
  }

  /**
   * Returns the corresponding text range for a type hint as returned by {@link #getTypeCommentValue(String)}.
   *
   * @see #getTypeCommentValue(String)
   */
  @Nullable
  public static TextRange getTypeCommentValueRange(@NotNull String text) {
    final Matcher m = TYPE_COMMENT_PATTERN.matcher(text);
    if (m.matches()) {
      final String hint = getTypeCommentValue(text);
      if (hint != null) {
        return TextRange.from(m.start(1), hint.length());
      }
    }
    return null;
  }
}
