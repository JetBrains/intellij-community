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

package com.intellij.util.xml.tree;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.ElementPresentationTemplate;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsProblemsHolder;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class DomElementsGroupNode extends AbstractDomElementNode {
  private final DomElement myParentElement;
  private final DomElement myRootDomElement;
  private final String myChildrenTagName;
  private final DomCollectionChildDescription myChildDescription;

  public DomElementsGroupNode(final DomElement modelElement, DomCollectionChildDescription description, SimpleNode parent,
                              final DomElement rootDomElement) {
    super(modelElement, parent);
    myParentElement = modelElement;
    myChildDescription = description;
    myChildrenTagName = description.getXmlElementName();
    myRootDomElement = rootDomElement;
  }

  @Override
  public SimpleNode[] getChildren() {
    if (!myParentElement.isValid()) return NO_CHILDREN;

    final List<SimpleNode> simpleNodes = new ArrayList<>();
    for (DomElement domChild : myChildDescription.getStableValues(myParentElement)) {
      if (shouldBeShown(domChild.getDomElementType())) {
        simpleNodes.add(new BaseDomElementNode(domChild, myRootDomElement, this));
      }
    }
    return simpleNodes.toArray(new SimpleNode[simpleNodes.size()]);
  }

  @Override
  @NotNull
  public Object[] getEqualityObjects() {
    return new Object[]{myParentElement, myChildrenTagName};
  }

  @Override
  protected void doUpdate() {
    setUniformIcon(getNodeIcon());

    clearColoredText();

    final boolean showErrors = hasErrors();
    final int childrenCount = getChildren().length;

    if (childrenCount > 0) {
      final SimpleTextAttributes textAttributes =
        showErrors ? getWavedAttributes(SimpleTextAttributes.STYLE_BOLD) :  new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, SimpleTextAttributes.REGULAR_ATTRIBUTES.getFgColor());

      addColoredFragment(getNodeName(), textAttributes);
      addColoredFragment(" (" + childrenCount + ')', showErrors ? IdeBundle.message("dom.elements.tree.childs.contain.errors") : null,
                         SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
    }
    else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
    }
  }

  private boolean hasErrors() {
    if (!myParentElement.isValid()) return false;

    for (DomElement domElement : myChildDescription.getStableValues(myParentElement)) {
      final DomElementAnnotationsManager annotationsManager = DomElementAnnotationsManager.getInstance(getProject());
      final DomElementsProblemsHolder holder = annotationsManager.getCachedProblemHolder(domElement);
      final List<DomElementProblemDescriptor> problems = holder.getProblems(domElement, true, HighlightSeverity.ERROR);
      if (problems.size() > 0) return true;
    }

    return false;
  }

  @Override
  public String getNodeName() {
    if (!myParentElement.isValid()) return "";

    return myChildDescription.getCommonPresentableName(myParentElement);
  }

  @Override
  public String getTagName() {
    return myChildrenTagName;
  }

  @Override
  public DomElement getDomElement() {
    return myParentElement;
  }


  public DomCollectionChildDescription getChildDescription() {
    return myChildDescription;
  }


  @Override
  public Icon getNodeIcon() {
    Class clazz = ReflectionUtil.getRawType(myChildDescription.getType());
//        Class arrayClass = Array.newInstance(clazz, 0).getClass();
    ElementPresentationTemplate template = myChildDescription.getPresentationTemplate();
    if (template != null) {
      return template.createPresentation(null).getIcon();
    }
    return ElementPresentationManager.getIconForClass(clazz);
  }
}
