/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

/**
 * @author Dmitry Avdeev
 */
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RenameInputValidatorRegistry {
  private static RenameInputValidatorRegistry ourInstance = new RenameInputValidatorRegistry();

  public static RenameInputValidatorRegistry getInstance() {
    return ourInstance;
  }

  private List<Pair<ElementFilter,Condition<String>>> myValidators = new ArrayList<Pair<ElementFilter,Condition<String>>>();

  private RenameInputValidatorRegistry() {
  }

  public void registerInputValidator(@NotNull ElementFilter filter, @NotNull Condition<String> validator) {
    myValidators.add(new Pair<ElementFilter,Condition<String>>(filter, validator));
  }

  @Nullable
  public Condition<String> getInputValidator(PsiElement element) {
    for (Pair<ElementFilter,Condition<String>> pair: myValidators) {
      if (pair.first.isAcceptable(element, element)) {
        return pair.second;
      }
    }
    return null;
  }
}
