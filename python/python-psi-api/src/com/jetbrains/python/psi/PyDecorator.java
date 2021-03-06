/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi;

import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jetbrains.annotations.Nullable;

/**
 * Covers a decorator call, e.g. <tt>@staticmethod</tt>.
 * Decorators happen contextually above the function definition, but are stored inside it for convenience.
 * <b>Note:</b>
 * In {@code @foo} form, {@code PyCallExpression}'s methods are related to invocation of {@code foo}
 * as decorator. In {@code @foo(...)} form, these very methods are related to the call that returns the decorator
 * to be applied. In either case, they are related to an invocation of {@code foo}.
 * User: dcheryasov
 */
public interface PyDecorator extends PyCallExpression, StubBasedPsiElement<PyDecoratorStub> {
  /**
   * @return the function being decorated, or null.
   */
  @Nullable
  PyFunction getTarget();

  /**
   * Return the expression directly after "@" or {@code null} if there is none.
   * <p>
   * In a syntactically correct program prior Python 3.9, where the grammar restrictions for decorators
   * were relaxed in PEP 614, this method is expected to return either a plain reference expression
   * (i.e. {@code foo.bar}, not {@code foo[0].bar}) or a call on such a reference.
   */
  @Nullable
  PyExpression getExpression();

  /**
   * True if the annotating function is a builtin, useful together with getName(). Implementation uses stub info.
   * @see PyElement#getName()
   */
  boolean isBuiltin();

  /**
   * @return true if invocation has a form of {@code @foo(...)}.
   */
  boolean hasArgumentList();

  /**
   * If {@link #getCallee()} result for this decorator is a reference expression, return its
   * {@link PyReferenceExpression#asQualifiedName()} and {@code null} otherwise.
   * <p>
   * Effectively, it means that the result is {@code null} for any non-trivial reference or a call target.
   * <p>
   * Examples:
   * <ul>
   *   <li>{@code @foo.bar} -> {@code foo.bar}</li>
   *   <li>{@code @foo.bar(42)} -> {@code foo.bar}</li>
   *   <li>{@code @(foo.bar)} -> {@code null}</li>
   *   <li>{@code @foo.bar[42]} -> {@code null}</li>
   *   <li>{@code @foo[42].bar} -> {@code null}</li>
   * </ul>
   * <p>
   * In a syntactically correct program prior Python 3.9, this method is expected to return a non-null value.
   * <p>
   * This value is persisted in stubs as {@link PyDecoratorStub#getQualifiedName()}.
   *
   * @see #getCallee()
   * @see PyReferenceExpression#asQualifiedName()
   * @see PyDecoratorStub#getQualifiedName()
   */
  @Nullable
  QualifiedName getQualifiedName();
}
