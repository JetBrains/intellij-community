/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Function declaration in source (the <code>def</code> and everything within).
 * User: yole
 * Date: 29.05.2005
 * Time: 23:01:03
 * To change this template use File | Settings | File Templates.
 */
public interface PyFunction extends PsiNamedElement, PyStatement, NameDefiner, PyDocStringOwner, StubBasedPsiElement<PyFunctionStub> {
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
