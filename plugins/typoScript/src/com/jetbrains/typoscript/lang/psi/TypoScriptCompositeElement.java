package com.jetbrains.typoscript.lang.psi;

import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * @author lene
 *         Date: 11.04.12
 */
public interface TypoScriptCompositeElement extends NavigatablePsiElement {
  IElementType getTokenType();
}
