/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.wizard;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class BeanPropertyListCellRenderer extends ColoredListCellRenderer{
  private final SimpleTextAttributes myAttrs1;
  private final SimpleTextAttributes myAttrs2;

  public BeanPropertyListCellRenderer() {
    myAttrs1 = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
    myAttrs2 = SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  protected void customizeCellRenderer(
    final JList list,
    final Object value,
    final int index,
    final boolean selected,
    final boolean hasFocus
  ) {
    final BeanProperty property = (BeanProperty)value;
    if(property == null){
      append(UIDesignerBundle.message("property.not.defined"), myAttrs2);
    }
    else{
      append(property.myName, myAttrs1);
      append(" ", myAttrs1);
      append(property.myType, myAttrs2);
    }
  }
}
