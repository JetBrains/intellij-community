package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
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

      Library library = myRootElement.getLibrary();
      final String[] sources = myParentEditor.getLibraryEditor(library).getUrls(OrderRootType.SOURCES);
      if (sources.length > 0) {
        elements.add(new SourcesElement(myRootElement));
      }

      final String[] javadocs = myParentEditor.getLibraryEditor(library).getUrls(OrderRootType.JAVADOC);
      if (javadocs.length > 0) {
        elements.add(new JavadocElement(myRootElement));
      }

      final String[] classes = myParentEditor.getLibraryEditor(library).getUrls(OrderRootType.CLASSES);
      if (classes.length > 0) {
        elements.add(new ClassesElement(myRootElement));
      }

      return elements.toArray();
    }

    if (element instanceof ClassesElement) {
      return buildItems(element, ((ClassesElement)element).getParent().getLibrary(), OrderRootType.CLASSES);
    }

    if (element instanceof SourcesElement) {
      return buildItems(element, ((SourcesElement)element).getParent().getLibrary(), OrderRootType.SOURCES);
    }

    if (element instanceof JavadocElement) {
      return buildItems(element, ((JavadocElement)element).getParent().getLibrary(), OrderRootType.JAVADOC);
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] buildItems(Object parent, Library library, OrderRootType orderRootType) {
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
    if (element instanceof ClassesElement) {
      return ((ClassesElement)element).getParent();
    }
    if (element instanceof SourcesElement) {
      return ((SourcesElement)element).getParent();
    }
    if (element instanceof JavadocElement) {
      return ((JavadocElement)element).getParent();
    }
    if (element instanceof ItemElement) {
      return ((ItemElement)element).getParent();
    }
    return myRootElement;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == myRootElement) {
      return myRootElementDescriptor;
    }
    if (element instanceof LibraryElement) {
      return new LibraryElementDescriptor(parentDescriptor, (LibraryElement)element, myParentEditor);
    }
    if (element instanceof ClassesElement) {
      return new ClassesElementDescriptor(parentDescriptor, (ClassesElement)element);
    }
    if (element instanceof SourcesElement) {
      return new SourcesElementDescriptor(parentDescriptor, (SourcesElement)element);
    }
    if (element instanceof JavadocElement) {
      return new JavadocElementDescriptor(parentDescriptor, (JavadocElement)element);
    }
    if (element instanceof ItemElement) {
      return new ItemElementDescriptor(parentDescriptor, (ItemElement)element);
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
