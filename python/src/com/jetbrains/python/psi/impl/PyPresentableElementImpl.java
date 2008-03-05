package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PyPresentableElementImpl extends PyElementImpl implements PsiNamedElement {
  public PyPresentableElementImpl(ASTNode astNode) {
    super(astNode);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name != null ? name : "<none>";
      }

      public String getLocationString() {
        return null;
      }

      public Icon getIcon(final boolean open) {
        return PyPresentableElementImpl.this.getIcon(open ? ICON_FLAG_OPEN : ICON_FLAG_CLOSED);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }
}
