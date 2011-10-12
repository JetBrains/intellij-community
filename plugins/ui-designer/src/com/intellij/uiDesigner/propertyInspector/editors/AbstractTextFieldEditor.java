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

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.InplaceContext;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public abstract class AbstractTextFieldEditor<V> extends PropertyEditor<V> {
  protected final JTextField myTf;

  protected AbstractTextFieldEditor() {
    myTf = new JTextField();
    myTf.addActionListener(new MyActionListener());
  }

  public void updateUI() {
    SwingUtilities.updateComponentTreeUI(myTf);
  }

  protected void setValueFromComponent(RadComponent component, V value) {
    myTf.setText(value == null ? "" : value.toString());
  }

  public JComponent getComponent(final RadComponent ignored, final V value, final InplaceContext inplaceContext) {
    setValueFromComponent(ignored, value);

    if(inplaceContext != null) {
      myTf.setBorder(UIUtil.getTextFieldBorder());
      if (inplaceContext.isStartedByTyping()) {
        myTf.setText(Character.toString(inplaceContext.getStartChar()));
      }
    }
    else{
      myTf.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    }

    return myTf;
  }

  protected final class MyActionListener implements ActionListener {
    public void actionPerformed(final ActionEvent e){
      fireValueCommitted(true, false);
    }
  }
}
