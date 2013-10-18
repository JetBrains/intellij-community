package com.jetbrains.rest.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.rest.psi.RestElement;
import com.jetbrains.rest.psi.RestTitle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Handles nodes in ReST Structure View.
 * User : catherine
 */
public class RestStructureViewElement implements StructureViewTreeElement {

  private NavigatablePsiElement myElement;

  public RestStructureViewElement(NavigatablePsiElement element) {
    myElement = element;
  }

  public NavigatablePsiElement getValue() {
    return myElement;
  }

  public void navigate(boolean requestFocus) {
    myElement.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return myElement.canNavigate();
  }

  public boolean canNavigateToSource() {
    return myElement.canNavigateToSource();
  }

  public StructureViewTreeElement[] getChildren() {
    final Set<RestElement> childrenElements = new LinkedHashSet<RestElement>();
    myElement.acceptChildren(new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof RestTitle && ((RestTitle)element).getName() != null)
          childrenElements.add((RestElement)element);
        else
          element.acceptChildren(this);
      }
    });
    StructureViewTreeElement[] children = new StructureViewTreeElement[childrenElements.size()];
    int i = 0;
    for (RestElement element : childrenElements) {
      children[i] = new RestStructureViewElement(element);
      i += 1;
    }

    return children;
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return myElement.getName();
      }

      @Nullable
      public String getLocationString() {
        return null;
      }

      public Icon getIcon(boolean open) {
        return null;
      }
    };
  }
}
