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
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependentNSReference extends BasicAttributeValueReference {
  @NotNull
  private final URLReference myReference;
  private final boolean myForceFetchResultValid;

  public DependentNSReference(final PsiElement element, TextRange range, @NotNull URLReference ref) {
    this(element, range, ref, false);
  }

  public DependentNSReference(final PsiElement element,
                              TextRange range,
                              @NotNull URLReference ref,
                              boolean valid) {
    super(element, range);
    myReference = ref;
    myForceFetchResultValid = valid;
  }

  @Override
  @Nullable
  public PsiElement resolve() {
    final String canonicalText = getCanonicalText();
    final PsiFile file = ExternalResourceManager.getInstance().getResourceLocation(canonicalText, myElement.getContainingFile(), null);
    if (file != null) return file;
    PsiElement element = myReference.resolve();
    if (element == null && !myForceFetchResultValid && !XmlUtil.isUrlText(canonicalText, myElement.getProject())) return myElement;  // file reference will highlight it
    return element;
  }

  @Override
  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  public boolean isForceFetchResultValid() {
    return myForceFetchResultValid;
  }

  @NotNull
  public URLReference getNamespaceReference() {
    return myReference;
  }
}
