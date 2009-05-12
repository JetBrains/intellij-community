package com.intellij.openapi.roots.ui.configuration.artifacts.nodes;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author nik
 */
public class PackagingElementNode<E extends PackagingElement<?>> extends ArtifactsTreeNode {
  protected final PackagingEditorContext myContext;
  private final List<E> myPackagingElements;
  private final PackagingElementPresentation myPresentation;

  public PackagingElementNode(@NotNull E packagingElement, PackagingEditorContext context, PackagingElementNode<?> parent) {
    super(context.getProject(), parent);
    myPackagingElements = new SmartList<E>();
    myPackagingElements.add(packagingElement);
    myContext = context;
    myPresentation = packagingElement.createPresentation(context);
  }

  @Override
  protected void update(PresentationData presentation) {
    myPresentation.render(presentation);
    presentation.setTooltip(myPresentation.getTooltipText());
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

  public PackagingElementPresentation getElementPresentation() {
    return myPresentation;
  }

  @Override
  public int getWeight() {
    return myPresentation.getWeight();
  }

  public E getFirstElement() {
    return myPackagingElements.get(0);
  }

  @Override
  public String getName() {
    return myPresentation.getPresentableName();
  }

  void addElement(PackagingElement<?> element) {
    myPackagingElements.add((E)element);
  }
}
