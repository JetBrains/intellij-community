package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Function declaration in source (the <code>def</code> and everything within).
 *
 * @author yole
 */
public interface PyFunction extends PsiNamedElement, PsiNameIdentifierOwner, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyFunctionStub>,
                                    ScopeOwner {
  PyFunction[] EMPTY_ARRAY = new PyFunction[0];
  
  /**
   * Returns the AST node for the function name identifier.
   *
   * @return the node, or null if the function is incomplete (only the "def"
   *         keyword was typed)
   */
  @Nullable
  ASTNode getNameNode();

  @NotNull
  PyParameterList getParameterList();

  @NotNull
  PyStatementList getStatementList();

  @Nullable
  PyClass getContainingClass();

  @Nullable
  PyDecoratorList getDecoratorList();

  /**
   * Returns true if the function is a top-level class (its parent is its containing file).
   *
   * @return true if the function is top-level, false otherwise.
   */
  boolean isTopLevel();

  /**
   * Flags that mark common alterations of a function: decoration by and wrapping in classmethod() and staticmethod().
   */
  enum Flag {
    /**
     * Function is decorated with @classmethod, first param is the class.
     */
    CLASSMETHOD,
    /**
     * Function is decorated with {@code @staticmethod}, first param is as in a regular function.
     */
    STATICMETHOD,

    /**
     * Function is not decorated, but wrapped in an actual call to {@code staticmethod} or {@code classmethod},
     * e.g. {@code foo = classmethod(foo)}. The callee is the inner version of {@code foo}, not the outer callable produced
     * by the wrapping call.
     */
    WRAPPED,
  }
}
