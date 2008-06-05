package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrariesAlphaComparator;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;

class LibraryTableTreeStructure extends AbstractLibraryTreeStructure {
  private final Object myRootElement = new Object();

  public LibraryTableTreeStructure(LibraryTableEditor parentEditor) {
    super(parentEditor);
    myRootElementDescriptor = new NodeDescriptor(null, null) {
      public boolean update() {
        myName = ProjectBundle.message("library.root.node");
        return false;
      }
      public Object getElement() {
        return myRootElement;
      }
    };
  }

  public Object getRootElement() {
    return myRootElement;
  }

  public Object[] getChildElements(Object element) {
    if (element == myRootElement) {
      final Library[] libraries = myParentEditor.getLibraries();
      Arrays.sort(libraries, LibrariesAlphaComparator.INSTANCE);
      LibraryElement[] elements = new LibraryElement[libraries.length];
      for (int idx = 0; idx < libraries.length; idx++) {
        final Library library = libraries[idx];
        boolean allPathsValid = true;
        for (OrderRootType type : OrderRootType.getAllTypes()) {
          allPathsValid &= allPathsValid(library, type);
        }
        elements[idx] = new LibraryElement(library, myParentEditor, !allPathsValid);
      }
      return elements;
    }

    if (element instanceof LibraryElement) {
      final LibraryElement libraryItemElement = (LibraryElement)element;
      ArrayList<LibraryTableTreeContentElement> elements = new ArrayList<LibraryTableTreeContentElement>(3);
      final Library library = libraryItemElement.getLibrary();

      final LibraryEditor parentEditor = myParentEditor.getLibraryEditor(library);
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        final String[] urls = parentEditor.getUrls(type);
        if (urls.length > 0) {
          elements.add(OrderRootTypeUIFactory.FACTORY.getByKey(type).createElement(libraryItemElement));
        }
      }
      return elements.toArray();
    }

    if (element instanceof LibraryTableTreeContentElement) {
      final LibraryTableTreeContentElement contentElement = (LibraryTableTreeContentElement)element;
      final LibraryTableTreeContentElement parentElement = contentElement.getParent();
      if (parentElement instanceof LibraryElement) {
        return buildItems(contentElement, ((LibraryElement)parentElement).getLibrary(), contentElement.getOrderRootType());
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private boolean allPathsValid(Library library, OrderRootType orderRootType) {
    return myParentEditor.getLibraryEditor(library).allPathsValid(orderRootType);
  }

}
