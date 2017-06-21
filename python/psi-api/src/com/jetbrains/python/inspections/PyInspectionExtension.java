/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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

  /**
   * Checks if unresolved member could be ignored.
   *
   * @param type type whose member will be checked
   * @param name member name
   * @return true if the unresolved member with the specified name could be ignored
   * @deprecated Use {@link PyInspectionExtension#ignoreUnresolvedMember(PyType, String, TypeEvalContext)} instead.
   * This method will be removed in 2018.1.
   */
  @Deprecated
  public boolean ignoreUnresolvedMember(@NotNull PyType type, @NotNull String name) {
    return false;
  }

  /**
   * Checks if unresolved member could be ignored.
   *
   * @param type    type whose member will be checked
   * @param name    member name
   * @param context type evaluation context
   * @return true if the unresolved member with the specified name could be ignored
   */
  public boolean ignoreUnresolvedMember(@NotNull PyType type, @NotNull String name, @NotNull TypeEvalContext context) {
    return ignoreUnresolvedMember(type, name);
  }

  /**
   * Returns true if access to protected (the one started with "_") symbol should not be treated as violation.
   *
   * @param expression access expression i.e. "_foo"
   * @param context    type eval to be used
   * @return true if ignore
   */
  public boolean ignoreProtectedSymbol(@NotNull final PyReferenceExpression expression, @NotNull final TypeEvalContext context) {
    return false;
  }

  public boolean ignoreInitNewSignatures(@NotNull PyFunction original, @NotNull PyFunction complementary) {
    return false;
  }
}
