/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteTypeDescriptionLocation;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PropertiesDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(@NotNull final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (element instanceof IProperty) {
      if (location instanceof DeleteTypeDescriptionLocation) {
        int count = ((DeleteTypeDescriptionLocation) location).isPlural() ? 2 : 1;
        return IdeBundle.message("prompt.delete.property", count);
      }
      if (location instanceof UsageViewLongNameLocation) {
        return ((IProperty) element).getKey();
      }
    }
    return null;
  }
}
