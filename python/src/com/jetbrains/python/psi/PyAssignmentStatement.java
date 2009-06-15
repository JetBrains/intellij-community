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

import org.jetbrains.annotations.Nullable;

/**
 * Describes an assignment statement.
 */
public interface PyAssignmentStatement extends PyStatement, NameDefiner {

  /**
   * @return the left-hand side of the statement; each item may consist of many elements.
   */
  PyExpression[] getTargets();


  /**
   * @return right-hand side of the statement; may as well consist of many elements.
   */
  @Nullable
  PyExpression getAssignedValue();

  /**
   * Applies a visitor to every element of left-hand side. Tuple elements are flattened down to their most nested
   * parts. E.g. if the target is <tt>a, b[1], (c(2).d, e.f)</tt>, then expressions
   * <tt>a</tt>, <tt>b[1]</tt>, <tt>c(2).d</tt>, <tt>e.f</tt> will be visited.
   * Order of visiting is not guaranteed.
   * @param visitor its {@link PyElementVisitor#visitPyExpression} method will be called for each elementary target expression
   */
  //void visitElementaryTargets(PyElementVisitor visitor);
}
