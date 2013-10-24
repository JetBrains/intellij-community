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
package com.jetbrains.python.psi.resolve;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author yole
 */
public class ImportedResolveResult extends RatedResolveResult {
  private final List<PsiElement> myNameDefiners;

  public ImportedResolveResult(PsiElement element, int rate, List<PsiElement> nameDefiners) {
    super(rate, element);
    myNameDefiners = nameDefiners;
  }

  public List<PsiElement> getNameDefiners() {
    return myNameDefiners;
  }

  @Override
  public RatedResolveResult replace(PsiElement what) {
    return new ImportedResolveResult(what, getRate(), myNameDefiners);
  }
}
