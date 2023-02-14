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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public class AttributeValueSelfReference extends BasicAttributeValueReference {
  public AttributeValueSelfReference(final PsiElement element) {
    super(element);
  }

  public AttributeValueSelfReference(final PsiElement element, int offset) {
    super(element, offset);
  }

  public AttributeValueSelfReference(final PsiElement element, TextRange range) {
    super(element, range);
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    return myElement;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
