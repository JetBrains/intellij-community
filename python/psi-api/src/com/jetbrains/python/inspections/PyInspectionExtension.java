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
package com.jetbrains.python.inspections;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public abstract class PyInspectionExtension {
  public static final ExtensionPointName<PyInspectionExtension> EP_NAME = ExtensionPointName.create("Pythonid.inspectionExtension");

  public boolean ignoreUnused(PsiElement local) {
    return false;
  }

  public boolean ignoreMissingDocstring(PyDocStringOwner docStringOwner) {
    return false;
  }

  public List<String> getFunctionParametersFromUsage(PsiElement elt) {
    return null;
  }

  public boolean ignoreMethodParameters(@NotNull PyFunction function) {
    return false;
  }

  public boolean ignorePackageNameInRequirements(@NotNull PyQualifiedExpression importedExpression) {
    return false;
  }

  public boolean ignoreUnresolvedReference(@NotNull PyElement node, @NotNull PsiReference reference) {
    return false;
  }

  public boolean ignoreUnresolvedMember(@NotNull PyType type, @NotNull String name) {
    return false;
  }
}
