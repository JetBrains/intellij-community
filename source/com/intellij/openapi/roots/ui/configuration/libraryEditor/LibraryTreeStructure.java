package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.OrderRootTypeUIFactory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

public class LibraryTreeStructure extends AbstractTreeStructure{
  private final LibraryElement myRootElement;
  private NodeDescriptor myRootElementDescriptor;
  private final LibraryTableEditor myParentEditor;

  public LibraryTreeStructure(LibraryTableEditor parentElement, Library library) {
    myParentEditor = parentElement;
    myRootElement = new LibraryElement(library, myParentEditor, false);
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
      ArrayList<LibraryTableTreeContentElement> elements = new ArrayList<LibraryTableTreeContentElement>(3);
      final Library library = myRootElement.getLibrary();
      final LibraryEditor parentEditor = myParentEditor.getLibraryEditor(library);
      for (OrderRootType type : OrderRootType.getAllTypes()) {
        final String[] urls = parentEditor.getUrls(type);
        if (urls.length > 0) {
          elements.add(OrderRootTypeUIFactory.FACTORY.getByKey(type).createElement(myRootElement));
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

  public Library getLibrary() {
    return myRootElement.getLibrary();
  }
}
