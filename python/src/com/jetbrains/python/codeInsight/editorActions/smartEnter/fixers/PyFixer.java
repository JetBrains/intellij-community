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
package com.jetbrains.python.codeInsight.editorActions.smartEnter.fixers;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.PyElement;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 */
public abstract class PyFixer<T extends PyElement> {
  private final Class<T> myClass;

  public PyFixer(@NotNull Class<T> aClass) {
    myClass = aClass;
  }

  @SuppressWarnings("unchecked")
  public final void apply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull PsiElement element)
    throws IncorrectOperationException {
    if (myClass.isInstance(element) && isApplicable(editor, (T)element)) {
      doApply(editor, processor, (T)element);
    }
  }
  
  protected boolean isApplicable(@NotNull Editor editor, @NotNull T element) {
    return myClass.isInstance(element);
  }

  protected abstract void doApply(@NotNull Editor editor, @NotNull PySmartEnterProcessor processor, @NotNull T element);
}
