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

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Handles find usage for magic literals.
 * <strong>Install it</strong> as {@link FindUsagesHandlerFactory#EP_NAME}
 * @author Ilya.Kazakevich
 */
public class PyMagicLiteralFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(@NotNull final PsiElement element) {
    return PyMagicLiteralTools.couldBeMagicLiteral(element);
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler(@NotNull final PsiElement element, final boolean forHighlightUsages) {
    return new MyFindUsagesHandler(element);
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    MyFindUsagesHandler(final PsiElement element) {
      super(element);
    }

    @Nullable
    @Override
    protected Collection<String> getStringsToSearch(@NotNull final PsiElement element) {
      return Collections.singleton(((StringLiteralExpression)element).getStringValue());
    }
  }
}
