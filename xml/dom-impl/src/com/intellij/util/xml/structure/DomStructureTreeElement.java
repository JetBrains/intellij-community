/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.xml.structure;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
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

  public DomStructureTreeElement(@NotNull final DomElement element,
                                 @NotNull final Function<DomElement,DomService.StructureViewMode> descriptor,
                                 @Nullable final DomElementNavigationProvider navigationProvider) {
    myElement = element;
    myDescriptor = descriptor;
    myNavigationProvider = navigationProvider;
  }

  public DomElement getElement() {
    return myElement;
  }

  @Override
  @Nullable
  public Object getValue() {
    return myElement.isValid() ? myElement.getXmlElement() : null;
  }

  @Override
  @NotNull
  public ItemPresentation getPresentation() {
    return this;
  }

  @Override
  @NotNull
  public TreeElement[] getChildren() {
    if (!myElement.isValid()) return EMPTY_ARRAY;
    final ArrayList<TreeElement> result = new ArrayList<>();
    final DomElementVisitor elementVisitor = new DomElementVisitor() {
      @Override
      public void visitDomElement(final DomElement element) {
        if (element instanceof GenericDomValue) return;
        final DomService.StructureViewMode viewMode = myDescriptor.fun(element);
        switch (viewMode) {
          case SHOW:
            result.add(createChildElement(element));
            break;
          case SHOW_CHILDREN:
            DomUtil.acceptAvailableChildren(element, this);
            break;
          case SKIP:
            break;
        }
      }
    };
    DomUtil.acceptAvailableChildren(myElement, elementVisitor);
    return result.toArray(new TreeElement[result.size()]);
  }

  protected StructureViewTreeElement createChildElement(final DomElement element) {
    return new DomStructureTreeElement(element, myDescriptor, myNavigationProvider);
  }

  @Override
  public void navigate(boolean requestFocus) {
    if (myNavigationProvider != null) myNavigationProvider.navigate(myElement, true);
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
    final ElementPresentation presentation = myElement.getPresentation();
    final String name = presentation.getElementName();
    return name != null? name : presentation.getTypeName();
  }

  @Override
  @Nullable
  public String getLocationString() {
    return null;
  }

  @Override
  @Nullable
  public Icon getIcon(boolean open) {
    if (!myElement.isValid()) return null;
    return myElement.getPresentation().getIcon();
  }
}
