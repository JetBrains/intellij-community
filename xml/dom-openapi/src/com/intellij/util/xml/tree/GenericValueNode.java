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

import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.DomElement;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public class GenericValueNode extends AbstractDomElementNode {
  protected GenericDomValue myModelElement;
  protected String myTagName;

  public GenericValueNode(final GenericDomValue modelElement, SimpleNode parent) {
    super(modelElement, parent);

    myModelElement = modelElement;
    myTagName = modelElement.getXmlElementName();
   }

  @Override
  public String getNodeName() {
    return getPropertyName();
  }

  @Override
  public String getTagName() {
    return myTagName;
  }

  @Override
  public DomElement getDomElement() {
    return myModelElement;
  }

  @Override
  protected void doUpdate() {
    setUniformIcon(getNodeIcon());
    clearColoredText();
    final String stringValue = myModelElement.getStringValue();
    final Object value = myModelElement.getValue();
    if (value instanceof Boolean) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment(String.valueOf(value), SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else if (stringValue != null) {
      addColoredFragment(getNodeName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("=", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      addColoredFragment("\"" + stringValue + "\"", SimpleTextAttributes.EXCLUDED_ATTRIBUTES);
    } else {
      addColoredFragment(getNodeName(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
  }

  @Override
  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  @Override
  @NotNull
  public Object[] getEqualityObjects() {
    return new Object[]{myModelElement};
  }
}
