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
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class PropertiesPsiTreeChangePreprocessor extends PsiTreeChangePreprocessorBase {
  private static final TokenSet CODE_BLOCK_ELEMENTS = TokenSet.create(PropertiesTokenTypes.VALUE_CHARACTERS,
                                                                      PropertiesTokenTypes.END_OF_LINE_COMMENT,
                                                                      PropertiesTokenTypes.WHITE_SPACE,
                                                                      PropertiesTokenTypes.KEY_VALUE_SEPARATOR);

  public PropertiesPsiTreeChangePreprocessor(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected void onTreeChanged(@NotNull PsiTreeChangeEventImpl event) {
    if (event.isGenericChange()) return;
    switch (event.getCode()) {
      case BEFORE_PROPERTY_CHANGE:
      case BEFORE_CHILD_REMOVAL:
      case BEFORE_CHILD_ADDITION:
      case BEFORE_CHILD_MOVEMENT:
      case BEFORE_CHILDREN_CHANGE:
      case BEFORE_CHILD_REPLACEMENT:
        return;
      case CHILD_ADDED:
        if (isCodeBlock(event.getChild())) return;
        break;
      case CHILD_REMOVED:
        if (isCodeBlock(event.getChild())) return;
        break;
      case CHILD_REPLACED:
        if (isCodeBlock(event.getOldChild()) || isCodeBlock(event.getNewChild())) return;
        break;
      case CHILD_MOVED:
        if (isCodeBlock(event.getChild())) return;
        break;
      case CHILDREN_CHANGED:
        if (isCodeBlock(event.getParent())) return;
      case PROPERTY_CHANGED:
        break;
    }
    doIncOutOfCodeBlockCounter();
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof PropertiesFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    throw new IllegalStateException();
  }

  private static boolean isCodeBlock(@NotNull PsiElement element) {
    return CODE_BLOCK_ELEMENTS.contains(PsiUtilCore.getElementType(element));
  }
}
