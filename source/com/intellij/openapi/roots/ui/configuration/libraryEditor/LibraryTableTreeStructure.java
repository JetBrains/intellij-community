package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.LibrariesAlphaComparator;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

class LibraryTableTreeStructure extends AbstractTreeStructure {
  private final Object myRootElement = new Object();
  private NodeDescriptor myRootElementDescriptor;
  private final LibraryTableEditor myParentEditor;

  public LibraryTableTreeStructure(LibraryTableEditor result) {
    myParentEditor = result;
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

  private Object[] buildItems(LibraryTableTreeContentElement parent, Library library, OrderRootType orderRootType) {
    ArrayList<ItemElement> items = new ArrayList<ItemElement>();
    final LibraryEditor libraryEditor = myParentEditor.getLibraryEditor(library);
    final String[] urls = libraryEditor.getUrls(orderRootType);
    Arrays.sort(urls, LibraryTableEditor.ourUrlComparator);
    for (String url : urls) {
      items.add(new ItemElement(parent, library, url, orderRootType, libraryEditor.isJarDirectory(url), libraryEditor.isValid(url, orderRootType)));
    }

    return items.toArray();
  }

  private boolean allPathsValid(Library library, OrderRootType orderRootType) {
    return myParentEditor.getLibraryEditor(library).allPathsValid(orderRootType);
  }

  public Object getParentElement(Object element) {
    if (element == myRootElement) {
      return null;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).getParent();
    }
    return myRootElement;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return myRootElementDescriptor;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).createDescriptor(parentDescriptor, myParentEditor);
    }
    return null;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }
}
