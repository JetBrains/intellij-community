package org.jetbrains.plugins.textmate.psi;

import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.tree.IElementType;

class TextMatePsiElement extends CompositePsiElement {
  protected TextMatePsiElement(IElementType type) {
    super(type);
  }
}
