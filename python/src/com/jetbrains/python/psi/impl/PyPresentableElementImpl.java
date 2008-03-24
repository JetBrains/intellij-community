package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
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
        return getElementLocation();
      }

      public Icon getIcon(final boolean open) {
        return PyPresentableElementImpl.this.getIcon(open ? ICON_FLAG_OPEN : ICON_FLAG_CLOSED);
      }

      public TextAttributesKey getTextAttributesKey() {
        return null;
      }
    };
  }

  protected String getElementLocation() {
    return "(" + getPackageForFile(getContainingFile()) + ")";
  }

  protected static String getPackageForFile(final PsiFile containingFile) {
    final VirtualFile vFile = containingFile.getVirtualFile();
    StringBuilder result = new StringBuilder(vFile != null ? vFile.getNameWithoutExtension() : containingFile.getName());
    PsiDirectory dir = containingFile.getContainingDirectory();
    while(dir != null) {
      if (!ExcludedFileIndex.getInstance(containingFile.getProject()).isInContent(dir.getVirtualFile())) {
        break;
      }
      if (dir.findFile("__init__.py") == null) break;
      result.insert(0, dir.getName() + ".");
      dir = dir.getParentDirectory();
    }
    return result.toString();
  }
}
