package com.intellij.ide.projectView.impl.nodes;

import com.intellij.openapi.roots.OrderEntry;

/**
 * @author Eugene Zhuravlev
 * Date: Sep 17, 2003
 * Time: 7:08:30 PM
 */
public final class NamedLibraryElement {
  private final LibraryGroupElement myParent;
  private final OrderEntry myEntry;

  public NamedLibraryElement(LibraryGroupElement parent, OrderEntry entry) {
    myParent = parent;
    myEntry = entry;
  }

  public LibraryGroupElement getParent() {
    return myParent;
  }

  public String getName() {
    return myEntry.getPresentableName();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamedLibraryElement)) return false;

    final NamedLibraryElement namedLibraryElement = (NamedLibraryElement)o;

    if (!myEntry.equals(namedLibraryElement.myEntry)) return false;
    if (!myParent.equals(namedLibraryElement.myParent)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myParent.hashCode();
    result = 29 * result + myEntry.hashCode();
    return result;
  }

  public OrderEntry getOrderEntry() {
    return myEntry;
  }
}
