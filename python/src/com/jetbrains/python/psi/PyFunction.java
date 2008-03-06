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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 23:01:03
 * To change this template use File | Settings | File Templates.
 */
public interface PyFunction extends PsiNamedElement, PyElement, PyDocStringOwner, PyFunctionStub {
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
}
