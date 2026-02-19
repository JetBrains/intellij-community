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

import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;


public class ImportedResolveResult extends RatedResolveResult {
  private final @Nullable PyImportedNameDefiner myDefiner;

  public ImportedResolveResult(@Nullable PsiElement element, int rate, @Nullable PyImportedNameDefiner definer) {
    super(rate, element);
    myDefiner = definer;
  }

  public @Nullable PyImportedNameDefiner getDefiner() {
    return myDefiner;
  }

  @Override
  public RatedResolveResult replace(PsiElement what) {
    return new ImportedResolveResult(what, getRate(), myDefiner);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ImportedResolveResult result = (ImportedResolveResult)o;
    return Objects.equals(myDefiner, result.myDefiner);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myDefiner);
  }
}
