/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class PyQualifiedNameFactory {
  @Nullable
  public static QualifiedName fromReferenceChain(List<PyExpression> components) {
    List<String> componentNames = new ArrayList<String>(components.size());
    for (PyExpression component : components) {
      final String refName = (component instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)component).getReferencedName() : null;
      if (refName == null) {
        return null;
      }
      componentNames.add(refName);
    }
    return QualifiedName.fromComponents(componentNames);
  }

  @Nullable
  public static QualifiedName fromExpression(PyExpression expr) {
    return expr instanceof PyReferenceExpression ? ((PyReferenceExpression) expr).asQualifiedName() : null;
  }
}
