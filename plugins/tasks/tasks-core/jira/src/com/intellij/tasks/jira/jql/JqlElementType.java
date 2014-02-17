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
package com.intellij.tasks.jira.jql;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

/**
 * @author Mikhail Golubev
 */
public class JqlElementType extends IElementType {
  private static final Class<?>[] PARAMETER_TYPES = {ASTNode.class};

  private final Class<? extends PsiElement> myClass;
  private Constructor<? extends PsiElement> myConstructor;

  public JqlElementType(@NotNull @NonNls String debugName) {
    this(debugName, ASTWrapperPsiElement.class);
  }

  public JqlElementType(@NotNull @NonNls String debugName, @NotNull Class<? extends PsiElement> cls) {
    super(debugName, JqlLanguage.INSTANCE);
    myClass = cls;
  }

  @Override
  public String toString() {
    return "JQL: " + super.toString();
  }

  @NotNull
  public PsiElement createElement(@NotNull ASTNode node) {
    try {
      if (myConstructor == null) {
        myConstructor = myClass.getConstructor(PARAMETER_TYPES);
      }
      return myConstructor.newInstance(node);
    }
    catch (Exception e) {
      throw new AssertionError(
        String.format("Class %s must have constructor accepting single ASTNode parameter", myClass.getName()));
    }
  }
}
