// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.util.Function;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
 */
public class DomStructureTreeElement implements StructureViewTreeElement, ItemPresentation {
  private final DomElement myElement;
  private final Function<DomElement, DomService.StructureViewMode> myDescriptor;
  private final DomElementNavigationProvider myNavigationProvider;

  public DomStructureTreeElement(final @NotNull DomElement element,
                                 final @NotNull Function<DomElement,DomService.StructureViewMode> descriptor,
                                 final @Nullable DomElementNavigationProvider navigationProvider) {
    myElement = element;
    myDescriptor = descriptor;
    myNavigationProvider = navigationProvider;
  }

  public DomElement getElement() {
    return myElement;
  }

  @Override
  public @Nullable Object getValue() {
    return myElement.isValid() ? myElement.getXmlElement() : null;
  }

  @Override
  public @NotNull ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public TreeElement @NotNull [] getChildren() {
    if (!myElement.isValid()) return EMPTY_ARRAY;
    final ArrayList<TreeElement> result = new ArrayList<>();
    final DomElementVisitor elementVisitor = new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        if (element instanceof GenericDomValue) return;
        final DomService.StructureViewMode viewMode = myDescriptor.fun(element);
        switch (viewMode) {
          case SHOW -> result.add(createChildElement(element));
          case SHOW_CHILDREN -> DomUtil.acceptAvailableChildren(element, this);
          case SKIP -> {}
        }
      }
    };
    DomUtil.acceptAvailableChildren(myElement, elementVisitor);
    return result.toArray(TreeElement.EMPTY_ARRAY);
  }

  protected StructureViewTreeElement createChildElement(final DomElement element) {
    return new DomStructureTreeElement(element, myDescriptor, myNavigationProvider);
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myNavigationProvider != null) myNavigationProvider.navigate(myElement, requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myNavigationProvider != null && myNavigationProvider.canNavigate(myElement);
  }

  @Override
  public boolean canNavigateToSource() {
    return myNavigationProvider != null && myNavigationProvider.canNavigate(myElement);
  }

  @Override
  public String getPresentableText() {
    if (!myElement.isValid()) return "<unknown>";
    try {
      ElementPresentation presentation = myElement.getPresentation();
      String name = presentation.getElementName();
      return name != null? name : presentation.getTypeName();
    }
    catch (IndexNotReadyException e) {
      return "Name not available during indexing";
    }
  }

  @Override
  public @Nullable Icon getIcon(boolean open) {
    if (!myElement.isValid()) return null;
    return myElement.getPresentation().getIcon();
  }
}
