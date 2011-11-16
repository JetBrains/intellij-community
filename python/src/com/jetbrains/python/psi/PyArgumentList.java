package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an argument list of a function call.
 *
 * @author yole
 */
public interface PyArgumentList extends PyElement {

  @NotNull PyExpression[] getArguments();

  @Nullable PyKeywordArgument getKeywordArgument(String name);

  void addArgument(PyExpression arg);
  void addArgumentFirst(PyExpression arg);
  void addArgumentAfter(PyExpression argument, PyExpression afterThis);

  /**
   * @return the call expression to which this argument list belongs; not null in correctly parsed cases.
   */
  @Nullable
  PyCallExpression getCallExpression();

  /**
   * Tries to map the argument list to callee's idea of parameters.
   * @return a result object with mappings and diagnostic flags.
   * @param resolveContext the reference resolution context
   */
  @NotNull
  CallArgumentsMapping analyzeCall(PyResolveContext resolveContext);

  @Nullable
  ASTNode getClosingParen();
}
