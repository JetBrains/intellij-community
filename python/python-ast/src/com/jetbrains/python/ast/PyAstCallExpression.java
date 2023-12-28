package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents an entire call expression, like <tt>foo()</tt> or <tt>foo.bar[1]('x')</tt>.
 */
@ApiStatus.Experimental
public interface PyAstCallExpression extends PyAstCallSiteExpression {

  @Override
  @Nullable
  default PyAstExpression getReceiver(@Nullable PyAstCallable resolvedCallee) {
    if (resolvedCallee instanceof PyAstFunction function) {
      if (!PyNames.NEW.equals(function.getName()) && function.getModifier() == PyAstFunction.Modifier.STATICMETHOD) {
        return null;
      }
    }

    final PyAstExpression callee = getCallee();
    if (callee != null && isImplicitlyInvokedMethod(resolvedCallee) && !Objects.equals(resolvedCallee.getName(), callee.getName())) {
      return callee;
    }

    if (callee instanceof PyAstQualifiedExpression) {
      return ((PyAstQualifiedExpression)callee).getQualifier();
    }

    return null;
  }

  @Contract("null -> false")
  private static boolean isImplicitlyInvokedMethod(@Nullable PyAstCallable resolvedCallee) {
    if (PyUtilCore.isInitOrNewMethod(resolvedCallee)) return true;

    if (resolvedCallee instanceof PyAstFunction function) {
      return PyNames.CALL.equals(function.getName()) && function.getContainingClass() != null;
    }

    return false;
  }

  @Override
  @NotNull
  default List<PyAstExpression> getArguments(@Nullable PyAstCallable resolvedCallee) {
    return Arrays.asList(getArguments());
  }

  /**
   * @return the expression representing the object being called (reference to a function).
   */
  @Nullable
  default PyAstExpression getCallee() {
    // peel off any parens, because we may have smth like (lambda x: x+1)(2)
    PsiElement seeker = getFirstChild();
    while (seeker instanceof PyAstParenthesizedExpression) seeker = ((PyAstParenthesizedExpression)seeker).getContainedExpression();
    return seeker instanceof PyAstExpression ? (PyAstExpression) seeker : null;
  }

  /**
   * @return the argument list used in the call.
   */
  @Nullable
  default PyAstArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyAstArgumentList.class);
  }

  /**
   * @return the argument array used in the call, or an empty array if the call has no argument list.
   */
  default PyAstExpression @NotNull [] getArguments() {
    final PyAstArgumentList argList = getArgumentList();
    return argList != null ? argList.getArguments() : PyAstExpression.EMPTY_ARRAY;
  }

  /**
   * If the list of arguments has at least {@code index} elements and the {@code index}'th element is of type {@code argClass},
   * returns it. Otherwise, returns null.
   *
   * @param index    argument index
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  default <T extends PsiElement> T getArgument(int index, @NotNull Class<T> argClass) {
    final PyAstExpression[] args = getArguments();
    return args.length > index ? ObjectUtils.tryCast(args[index], argClass) : null;
  }

  /**
   * Returns the argument marked with the specified keyword or the argument at the specified position, if one is present in the list.
   *
   * @param index    argument index
   * @param keyword  argument keyword
   * @param argClass argument expected type
   * @return the argument or null
   */
  @Nullable
  default <T extends PsiElement> T getArgument(int index, @NonNls @NotNull String keyword, @NotNull Class<T> argClass) {
    final PyAstExpression arg = getKeywordArgument(keyword);
    if (arg != null) {
      return ObjectUtils.tryCast(arg, argClass);
    }
    return getArgument(index, argClass);
  }

  /**
   * Returns the argument marked with the specified keyword, if one is present in the list.
   *
   * @param keyword argument keyword
   * @return the argument or null
   */
  @Nullable
  default PyAstExpression getKeywordArgument(@NotNull String keyword) {
    for (PyAstExpression arg : getArguments()) {
      if (arg instanceof PyAstKeywordArgument keywordArg) {
        if (keyword.equals(keywordArg.getKeyword())) {
          return keywordArg.getValueExpression();
        }
      }
    }
    return null;
  }

  /**
   * Checks if the unqualified name of the callee matches any of the specified names
   *
   * @param nameCandidates names to check
   * @return true if matches, false otherwise
   */
  default boolean isCalleeText(String @NotNull ... nameCandidates) {
    final PyAstExpression callee = getCallee();

    return callee instanceof PyAstReferenceExpression &&
           ContainerUtil.exists(nameCandidates, name -> name.equals(((PyAstReferenceExpression)callee).getReferencedName()));
  }
}
