package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;

class SourcesElement extends LibraryTableTreeContentElement {
    private final LibraryElement myParent;

    public SourcesElement(LibraryElement parent) {
      myParent = parent;
    }

    public LibraryElement getParent() {
      return myParent;
    }

  public OrderRootType getOrderRootType() {
    return OrderRootType.SOURCES;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor) {
    return new SourcesElementDescriptor(parentDescriptor, this);
  }

  public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof SourcesElement)) return false;

      final SourcesElement sourcesElement = (SourcesElement)o;

      if (!myParent.equals(sourcesElement.myParent)) return false;

      return true;
    }

    public int hashCode() {
      return myParent.hashCode();
    }
  }
