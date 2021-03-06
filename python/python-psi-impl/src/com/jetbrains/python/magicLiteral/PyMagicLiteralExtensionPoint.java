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
package com.jetbrains.python.magicLiteral;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Any magic literal extension point should implement this interface and be installed as extension point
 * using {@link #EP_NAME}
 *
 * @author Ilya.Kazakevich
 */
public interface PyMagicLiteralExtensionPoint {

  ExtensionPointName<PyMagicLiteralExtensionPoint> EP_NAME = ExtensionPointName.create("Pythonid.magicLiteral");


  /**
   * Checks if literal is magic and supported by this extension point.
   * @param element element to check
   * @return true if magic.
   */
  boolean isMagicLiteral(@NotNull StringLiteralExpression element);


  /**
   * @return human-readable type of this literal. Actually, that is extension point name
   */
  @NotNull
  String getLiteralType();

  boolean isEnabled(@NotNull final PsiElement anchor);
}
