package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;

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
        return getElementLocation();
      }

      public Icon getIcon(final boolean open) {
        return PyPresentableElementImpl.this.getIcon(open ? ICON_FLAG_OPEN : ICON_FLAG_CLOSED);
      }
    };
  }

  protected String getElementLocation() {
    return "(" + getPackageForFile(getContainingFile()) + ")";
  }

  public static String getPackageForFile(final PsiFile containingFile) {
    final VirtualFile vFile = containingFile.getVirtualFile();

    if (vFile != null) {
      final String importableName = ResolveImportUtil.findShortestImportableName(containingFile, vFile);
      if (importableName != null) {
        return importableName;
      }
    }
    return "";
  }
}
