// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
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

  public PropertiesPsiTreeChangePreprocessor(@NotNull Project project) {
    super(project);
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
