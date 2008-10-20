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

import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a generalized expression, possibly typed.
 * User: yole
 * Date: 29.05.2005
 */
public interface PyExpression extends PyElement {
  PyExpression[] EMPTY_ARRAY = new PyExpression[0];
  List<PyExpression> EMPTY_LIST = new ArrayList<PyExpression>(0);

  @Nullable
  PyType getType();
}
