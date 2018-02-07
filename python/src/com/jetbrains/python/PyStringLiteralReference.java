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
package com.jetbrains.python;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

/**
 * Some reference for string literal (handles rename and range correctly)
 *
 * @author Ilya.Kazakevich
 */
public abstract class PyStringLiteralReference extends BaseReference {
  @NotNull
  protected final StringLiteralExpression myStringLiteral;

  protected PyStringLiteralReference(@NotNull final StringLiteralExpression element) {
    super(element);
    myStringLiteral = element;
  }

  @SuppressWarnings("RefusedBequest") // 1 instead of 1 in range and "-1" at the end because we do not need quotes
  @Override
  public final TextRange getRangeInElement() {
    return myStringLiteral.getStringValueTextRange();
  }

  @Override
  public PsiElement handleElementRename(@NotNull final String newElementName) {
    final PsiElement newString = PyElementGenerator.getInstance(myElement.getProject()).createStringLiteral(myStringLiteral, newElementName);
    myStringLiteral.replace(newString);
    return newString;
  }
}
