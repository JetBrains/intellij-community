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
package com.jetbrains.python.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class PyElementType extends IElementType {
  @NotNull private final Function<? super ASTNode, ? extends PsiElement> myPsiCreator;

  private final String mySpecialMethodName;

  public PyElementType(@NotNull @NonNls String debugName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = null;
  }

  public PyElementType(@NotNull @NonNls String debugName, @NotNull Function<? super ASTNode, ? extends PsiElement> creator) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = creator;
    mySpecialMethodName = null;
  }

  public PyElementType(@NotNull @NonNls String debugName, @NotNull @NonNls String specialMethodName) {
    super(debugName, PythonFileType.INSTANCE.getLanguage());
    myPsiCreator = node -> { throw new IllegalStateException("Cannot create an element for " + node.getElementType() + " without element class");};
    mySpecialMethodName = specialMethodName;
  }

  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    return myPsiCreator.apply(node);
  }

  /**
   * @return name of special method for operation marked by this token; e.g. "__add__" for "+".
   */
  @Nullable
  public String getSpecialMethodName() {
    return mySpecialMethodName;
  }

  @Override
  public String toString() {
    return "Py:" + super.toString();
  }
}
