package com.intellij.openapi.roots.ui.configuration.libraryEditor;

class SourcesElement extends LibraryTableTreeContentElement {
    private final LibraryElement myParent;

    public SourcesElement(LibraryElement parent) {
      myParent = parent;
    }

    public LibraryElement getParent() {
      return myParent;
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
