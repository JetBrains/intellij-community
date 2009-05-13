package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class PackagingElementNode<E extends PackagingElement<?>> extends ArtifactsTreeNode {
  private final List<E> myPackagingElements;

  public PackagingElementNode(@NotNull E packagingElement, PackagingEditorContext context, PackagingElementNode<?> parent) {
    super(context, parent, packagingElement.createPresentation(context));
    myPackagingElements = new SmartList<E>();
    myPackagingElements.add(packagingElement);
  }

  public List<E> getPackagingElements() {
    return myPackagingElements;
  }

  @Nullable
  public E getElementIfSingle() {
    return myPackagingElements.size() == 1 ? myPackagingElements.get(0) : null;
  }

  @Override
  public Object[] getEqualityObjects() {
    return myPackagingElements.toArray(new Object[myPackagingElements.size()]);
  }

  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  public E getFirstElement() {
    return myPackagingElements.get(0);
  }

  void addElement(PackagingElement<?> element) {
    myPackagingElements.add((E)element);
  }
}
