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
 * Describes "from ... import" statements.
 */
public interface PyFromImportStatement extends PyStatement {
  boolean isStarImport();

  /**
   * Returns a reference the module from which import is required.
   * @return reference to module. If the 'from' reference is relative and consists entirely of dots, null is returned.
   */
  @Nullable PyReferenceExpression getImportSource();

  /**
   * @return elements that constitute the "import" clause
   */
  PyImportElement[] getImportElements();

  /**
   * @return the star in "from ... import *"
   */
  @Nullable PyStarImportElement getStarImportElement();

  /**
   * @return number of dots in relative "from" clause, or 0 in absolute import.
   */
  int getRelativeLevel();
}
