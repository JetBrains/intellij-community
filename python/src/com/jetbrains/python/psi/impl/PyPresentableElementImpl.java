package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;

import javax.swing.*;

/**
 * @author yole
 */
public abstract class PyPresentableElementImpl<T extends StubElement> extends PyBaseElementImpl<T> implements PsiNamedElement {
  public PyPresentableElementImpl(ASTNode astNode) {
    super(astNode);
  }

  protected PyPresentableElementImpl(final T stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        final String name = getName();
        return name != null ? name : "<none>";
      }

      public String getLocationString() {
        return "(" + getContainingFile().getName() + ")";
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
