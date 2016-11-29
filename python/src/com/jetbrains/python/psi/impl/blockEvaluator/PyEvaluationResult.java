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
package com.jetbrains.python.psi.impl.blockEvaluator;

import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ilya.Kazakevich
 */
@SuppressWarnings("PackageVisibleField") // Package-only class
class PyEvaluationResult {
  @NotNull
  final Map<String, Object> myNamespace = new HashMap<>();
  @NotNull
  final Map<String, List<PyExpression>> myDeclarations = new HashMap<>();

  @NotNull
  List<PyExpression> getDeclarations(@NotNull final String name) {
    final List<PyExpression> expressions = myDeclarations.get(name);
    return (expressions != null) ? expressions : Collections.<PyExpression>emptyList();
  }
}
