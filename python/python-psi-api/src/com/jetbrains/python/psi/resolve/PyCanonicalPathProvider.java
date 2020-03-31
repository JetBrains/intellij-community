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
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
 *
 * @author yole
 */
public interface PyCanonicalPathProvider {
  ExtensionPointName<PyCanonicalPathProvider> EP_NAME = ExtensionPointName.create("Pythonid.canonicalPathProvider");

  /**
   * Allows to provide a custom qualified name when a specific symbol is going to be imported into a specific file.
   *
   * @param qName    the real qualified name of the symbol being imported.
   * @param foothold the location where the symbol is being imported.
   * @return the qualified name to use in the import statement, or null if no replacement is necessary.
   */
  @Nullable
  QualifiedName getCanonicalPath(@NotNull QualifiedName qName, @Nullable PsiElement foothold);
}
