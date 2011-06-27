package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.util.ArrayFactory;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

/**
 * Function declaration in source (the <code>def</code> and everything within).
 *
 * @author yole
 */
public interface PyFunction
extends
  PsiNamedElement, StubBasedPsiElement<PyFunctionStub>,
  PsiNameIdentifierOwner, PyStatement, Callable, NameDefiner, PyDocStringOwner, ScopeOwner, PyDecoratable {

  PyFunction[] EMPTY_ARRAY = new PyFunction[0];
  ArrayFactory<PyFunction> ARRAY_FACTORY = new ArrayFactory<PyFunction>() {
    @Override
    public PyFunction[] create(int count) {
      return new PyFunction[count];
    }
  };

  /**
   * Returns the AST node for the function name identifier.
   *
   * @return the node, or null if the function is incomplete (only the "def"
   *         keyword was typed)
   */
  @Nullable
  ASTNode getNameNode();

  @Nullable
  PyStatementList getStatementList();

  @Nullable
  PyClass getContainingClass();

  /**
   * Returns true if the function is a top-level class (its parent is its containing file).
   *
   * @return true if the function is top-level, false otherwise.
   */
  boolean isTopLevel();

  @Nullable
  PyType getReturnTypeFromDocString();

  /**
   * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning..
   *
   * @return the deprecation message or null if the function is not deprecated.
   */
  String getDeprecationMessage();

  /**
   * Flags that mark common alterations of a function: decoration by and wrapping in classmethod() and staticmethod().
   */
  enum Flag {
    /**
     * Function is decorated with @classmethod, its first param is the class.
     */
    CLASSMETHOD,
    /**
     * Function is decorated with {@code @staticmethod}, its first param is as in a regular function.
     */
    STATICMETHOD,

    /**
     * Function is not decorated, but wrapped in an actual call to {@code staticmethod} or {@code classmethod},
     * e.g. {@code foo = classmethod(foo)}. The callee is the inner version of {@code foo}, not the outer callable produced
     * by the wrapping call.
     */
    WRAPPED,
  }

  /**
   * Returns a property for which this function is a getter, setter or deleter.
   *
   * @return the corresponding property, or null if there isn't any.
   */
  @Nullable
  Property getProperty();

  @Nullable
  PyAnnotation getAnnotation();
}
