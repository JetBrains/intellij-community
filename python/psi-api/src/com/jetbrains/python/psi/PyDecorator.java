package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.Nullable;

/**
 * Covers a decorator call, e.g. <tt>@staticmethod</tt>.
 * Decorators happen contextually above the function definition, but are stored inside it for convenience.
 * <b>Note:</b>
 * In <code>@foo</code> form, <code>PyCallExpression</code>'s methods are related to invocation of <code>foo</code>
 * as decorator. In <code>@foo(...)</code> form, these very methods are related to the call that returns the decorator
 * to be applied. In either case, they are related to an invocation of <code>foo</code>.
 * User: dcheryasov
 * Date: Sep 26, 2008
 */
public interface PyDecorator extends PyCallExpression, StubBasedPsiElement<PyDecoratorStub> {
  /**
   * @return the function being decorated, or null.
   */
  @Nullable
  PyFunction getTarget();

  /**
   * True if the annotating function is a builtin, useful togeter with getName(). Implementation uses stub info.
   * @see com.jetbrains.python.psi.PyElement#getName()
   */
  boolean isBuiltin();

  /**
   * @return true if invocation has a form of <code>@foo(...)</code>.
   */
  boolean hasArgumentList();

  /**
   * For cases when decorators are referenced via a qualified name, e.g. @property.setter.
   * @return dot-separated qualified name, or just {@link #getName()}'s value if no qualifiers are present.
   */
  @Nullable
  QualifiedName getQualifiedName();

}
