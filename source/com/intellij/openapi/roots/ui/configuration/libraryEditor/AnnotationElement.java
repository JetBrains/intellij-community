package com.intellij.openapi.roots.ui.configuration.libraryEditor;

class AnnotationElement extends LibraryTableTreeContentElement {
    private final LibraryElement myParent;

    public AnnotationElement(LibraryElement parent) {
      myParent = parent;
    }

    public LibraryElement getParent() {
      return myParent;
    }

    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AnnotationElement)) return false;

      final AnnotationElement annotationElement = (AnnotationElement)o;

      if (!myParent.equals(annotationElement.myParent)) return false;

      return true;
    }

    public int hashCode() {
      return myParent.hashCode();
    }
  }