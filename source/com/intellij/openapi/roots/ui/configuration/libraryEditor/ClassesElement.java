package com.intellij.openapi.roots.ui.configuration.libraryEditor;

class ClassesElement extends LibraryTableTreeContentElement {
  private final LibraryElement myParent;

  public ClassesElement(LibraryElement parent) {
    myParent = parent;
  }

  public LibraryElement getParent() {
    return myParent;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassesElement)) return false;

    final ClassesElement classesElement = (ClassesElement)o;

    if (!myParent.equals(classesElement.myParent)) return false;

    return true;
  }

  public int hashCode() {
    return myParent.hashCode();
  }
}
