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
   * True if the annotating function is a builtin, useful together with getName(). Implementation uses stub info.
   * @see com.jetbrains.python.psi.PyElement#getName()
   */
  boolean isBuiltin();

  /**
   * @return true if invocation has a form of {@code @foo(...)}.
   */
  boolean hasArgumentList();

  /**
   * For cases when decorators are referenced via a qualified name, e.g. @property.setter.
   * @return dot-separated qualified name, or just {@link #getName()}'s value if no qualifiers are present.
   */
  @Nullable
  QualifiedName getQualifiedName();

}
