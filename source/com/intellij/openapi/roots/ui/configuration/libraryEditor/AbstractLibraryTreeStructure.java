package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author nik
 */
public abstract class AbstractLibraryTreeStructure extends AbstractTreeStructure {
  protected NodeDescriptor myRootElementDescriptor;
  protected final LibraryTableEditor myParentEditor;

  public AbstractLibraryTreeStructure(LibraryTableEditor parentElement) {
    myParentEditor = parentElement;
  }

  public void commit() {
  }

  public boolean hasSomethingToCommit() {
    return false;
  }

  protected Object[] buildItems(LibraryTableTreeContentElement parent, Library library, OrderRootType orderRootType) {
    ArrayList<ItemElement> items = new ArrayList<ItemElement>();
    final LibraryEditor libraryEditor = myParentEditor.getLibraryEditor(library);
    final String[] urls = libraryEditor.getUrls(orderRootType).clone();
    Arrays.sort(urls, LibraryTableEditor.ourUrlComparator);
    for (String url : urls) {
      items.add(new ItemElement(parent, library, url, orderRootType, libraryEditor.isJarDirectory(url), libraryEditor.isValid(url, orderRootType)));
    }
    return items.toArray();
  }

  public Object getParentElement(Object element) {
    Object rootElement = getRootElement();
    if (element == rootElement) {
      return null;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).getParent();
    }
    return rootElement;
  }

  @NotNull
  public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
    if (element == getRootElement()) {
      return myRootElementDescriptor;
    }
    if (element instanceof LibraryTableTreeContentElement) {
      return ((LibraryTableTreeContentElement)element).createDescriptor(parentDescriptor, myParentEditor);
    }
    return null;
  }
}
