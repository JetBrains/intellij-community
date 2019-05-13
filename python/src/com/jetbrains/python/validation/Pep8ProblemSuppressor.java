/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.validation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Custom filter that allows to selectively suppress warnings and errors produced by pycodestyle.py (former pep8.py).
 * Note that by using {@link com.jetbrains.python.inspections.PythonVisitorFilter} you can disable PEP 8 inspection for concrete files
 * altogether.
 *
 * @author Mikhail Golubev
 * @see Pep8ExternalAnnotator
 * @see com.jetbrains.python.inspections.PythonVisitorFilter
 */
public interface Pep8ProblemSuppressor {
  ExtensionPointName<Pep8ProblemSuppressor> EP_NAME = ExtensionPointName.create("Pythonid.pep8ProblemSuppressor");

  /**
   * @param problem       a single problem returned by the script and extracted from its output
   * @param file          PSI file where the inspection operates
   * @param targetElement PSI element found in the place of a problem on which annotation is going be attached in the editor
   * @return whether notification about this problem should be hidden in the editor
   */
  boolean isProblemSuppressed(@NotNull Pep8ExternalAnnotator.Problem problem,
                              @NotNull PsiFile file,
                              @Nullable PsiElement targetElement);
}
