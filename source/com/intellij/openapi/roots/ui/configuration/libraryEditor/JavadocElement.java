package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.JavadocOrderRootType;
import com.intellij.openapi.roots.OrderRootType;

class JavadocElement extends LibraryTableTreeContentElement {
    private final LibraryElement myParent;

    public JavadocElement(LibraryElement parent) {
      myParent = parent;
    }

    public LibraryElement getParent() {
      return myParent;
    }

  public OrderRootType getOrderRootType() {
    return JavadocOrderRootType.INSTANCE;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor) {
    return new JavadocElementDescriptor(parentDescriptor, this);
  }

  public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof JavadocElement)) return false;

      final JavadocElement javadocElement = (JavadocElement)o;

      if (!myParent.equals(javadocElement.myParent)) return false;

      return true;
    }

    public int hashCode() {
      return myParent.hashCode();
    }
  }
