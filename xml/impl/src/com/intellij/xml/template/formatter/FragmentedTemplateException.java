/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.xml.template.formatter;

import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown if there are syntax errors in the template which make it difficult or impossible to merge a mix of template and markup/data
 * languages.
 */
public class FragmentedTemplateException extends Exception {
  private PsiErrorElement myErrorElement;

  public FragmentedTemplateException() {
  }

  public FragmentedTemplateException(@Nullable PsiErrorElement errorElement) {
    myErrorElement = errorElement;
  }

  @Nullable
  public PsiErrorElement getErrorElement() {
    return myErrorElement;
  }
}
