package com.intellij.openapi.roots.ui.configuration.libraryEditor;

class JavadocElement extends LibraryTableTreeContentElement {
    private final LibraryElement myParent;

    public JavadocElement(LibraryElement parent) {
      myParent = parent;
    }

    public LibraryElement getParent() {
      return myParent;
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
