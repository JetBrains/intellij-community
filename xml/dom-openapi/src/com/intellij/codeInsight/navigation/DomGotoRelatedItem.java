/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.util.xml.DomElement;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class DomGotoRelatedItem extends GotoRelatedItem {
  private final DomElement myElement;

  public DomGotoRelatedItem(DomElement element) {
    this(element, "XML");
  }

  public DomGotoRelatedItem(DomElement element, String group) {
    this(element, group, -1);
  }

  public DomGotoRelatedItem(DomElement element, String group, int mnemonic) {
    super(element.getXmlElement(), group, mnemonic);
    myElement = element;
  }

  @Override
  public String getCustomName() {
    return myElement.getPresentation().getElementName();
  }

  @Override
  public Icon getCustomIcon() {
    return myElement.getPresentation().getIcon();
  }
}
