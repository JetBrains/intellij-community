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

import com.intellij.psi.StubBasedPsiElement;
import com.jetbrains.python.psi.stubs.PyParameterListStub;

/**
 * Represents function parameter list.
 * Date: 29.05.2005
 */
public interface PyParameterList extends PyElement, StubBasedPsiElement<PyParameterListStub>, NameDefiner {

  /**
   * Extracts the individual parameters.
   * Note that tuple parameters are flattened by this method.
   * @return a possibly empty array of named paramaters.
   */
  PyParameter[] getParameters();

  /**
   * Adds a paramter to list, after all other parameters.
   * @param param what to add
   */
  void addParameter(PyParameter param);

  /**
   * Python 2.x allows for declarations like {@code def foo(a, (b, c))} that auto-unpack complex tuple parameters.
   * From caller side, such functions contain a smaller number of parameters, some of them unnamed and structurally constrained.  
   * (This is considered evil and eschewed in Py3k.)
   * @return true if the parameter list contains a tuple-based declaration.
   */
  boolean containsTuples();
}
