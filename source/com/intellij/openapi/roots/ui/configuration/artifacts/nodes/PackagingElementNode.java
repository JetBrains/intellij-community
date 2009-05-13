package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import com.intellij.openapi.util.MultiValuesMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author nik
 */
public class PackagingElementNode<E extends PackagingElement<?>> extends ArtifactsTreeNode {
  private final List<E> myPackagingElements;
  private final Map<PackagingElement<?>, CompositePackagingElement<?>> myParentElements = new HashMap<PackagingElement<?>, CompositePackagingElement<?>>(1);
  private final MultiValuesMap<PackagingElement<?>, PackagingNodeSource> myNodeSources = new MultiValuesMap<PackagingElement<?>, PackagingNodeSource>();
  private final CompositePackagingElementNode myParentNode;

  public PackagingElementNode(@NotNull E packagingElement, PackagingEditorContext context, @Nullable CompositePackagingElementNode parentNode,
                              @Nullable CompositePackagingElement<?> parentElement,
                              @NotNull Collection<PackagingNodeSource> nodeSources) {
    super(context, parentNode, packagingElement.createPresentation(context));
    myParentNode = parentNode;
    myParentElements.put(packagingElement, parentElement);
    myNodeSources.putAll(packagingElement, nodeSources);
    myPackagingElements = new SmartList<E>();
    myPackagingElements.add(packagingElement);
  }

  @Nullable 
  public CompositePackagingElement<?> getParentElement(PackagingElement<?> element) {
    return myParentElements.get(element);
  }

  @Nullable
  public CompositePackagingElementNode getParentNode() {
    return myParentNode;
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

  void addElement(PackagingElement<?> element, CompositePackagingElement parentElement, Collection<PackagingNodeSource> nodeSource) {
    myPackagingElements.add((E)element);
    myParentElements.put(element, parentElement);
    myNodeSources.putAll(element, nodeSource);
  }

  @NotNull
  public Collection<PackagingNodeSource> getNodeSources() {
    return myNodeSources.values();
  }

  @NotNull
  public Collection<PackagingNodeSource> getNodeSource(@NotNull PackagingElement<?> element) {
    final Collection<PackagingNodeSource> nodeSources = myNodeSources.get(element);
    return nodeSources != null ? nodeSources : Collections.<PackagingNodeSource>emptyList();
  }

  public PackagingEditorContext getContext() {
    return myContext;
  }
}
