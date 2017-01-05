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

package com.jetbrains.python.psi;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;

public interface PyCustomPackageIdentifier {
  ExtensionPointName<PyCustomPackageIdentifier> EP_NAME = ExtensionPointName.create("Pythonid.customPackageIdentifier");

  /**
   * Checks whether the given PsiDirectory can be treated as Python package.
   * Used to loosen the default package checking behavior in PyUtil#isPackage.
   */
  boolean isPackage(PsiDirectory directory);

  /**
   * Checks whether the given PsiFile is a python package file (i.e. an __init__.py file).
   * Use to treat files other than __init__.py files as python package files.
   */
  boolean isPackageFile(PsiFile file);
}

