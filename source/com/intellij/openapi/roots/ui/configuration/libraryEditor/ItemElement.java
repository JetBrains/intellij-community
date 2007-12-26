package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;


class ItemElement extends LibraryTableTreeContentElement {
  private final LibraryTableTreeContentElement myParent;
  private final Library myLibrary;
  private final String myUrl;
  private final OrderRootType myRootType;
  private final boolean myIsJarDirectory;
  private boolean myValid;

  public ItemElement(LibraryTableTreeContentElement parent, Library library, String url, OrderRootType rootType, final boolean isJarDirectory, boolean isValid) {
    myParent = parent;
    myLibrary = library;
    myUrl = url;
    myRootType = rootType;
    myIsJarDirectory = isJarDirectory;
    myValid = isValid;
  }

  public LibraryTableTreeContentElement getParent() {
    return myParent;
  }

  public OrderRootType getOrderRootType() {
    return null;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor) {
    return new ItemElementDescriptor(parentDescriptor, this);
  }

  public String getUrl() {
    return myUrl;
  }
    
  public boolean isJarDirectory() {
    return myIsJarDirectory;
  }
  
  public boolean isValid() {
    return myValid;
  }

  public OrderRootType getRootType() {
    return myRootType;
  }

  public Library getLibrary() {
    return myLibrary;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ItemElement)) return false;

    final ItemElement itemElement = (ItemElement)o;

    if (!myLibrary.equals(itemElement.myLibrary)) return false;
    if (!myParent.equals(itemElement.myParent)) return false;
    if (!myRootType.equals(itemElement.myRootType)) return false;
    if (!myUrl.equals(itemElement.myUrl)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myParent.hashCode();
    result = 29 * result + myLibrary.hashCode();
    result = 29 * result + myUrl.hashCode();
    result = 29 * result + myRootType.hashCode();
    return result;
  }
}
