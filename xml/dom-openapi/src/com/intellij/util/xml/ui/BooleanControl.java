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
package com.intellij.util.xml.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author peter
 */
public class BooleanControl extends BaseModifiableControl<JCheckBox, Boolean> {
  private boolean myUndefined;

  public BooleanControl(final DomWrapper<Boolean> domWrapper) {
    super(domWrapper);
  }

  @Override
  protected JCheckBox createMainComponent(JCheckBox boundComponent) {
    JCheckBox checkBox = boundComponent == null ? new JCheckBox() : boundComponent;

    checkBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myUndefined = false;
        setModified();
        commit();
        reset();
      }
    });
    return checkBox;
  }

  @Override
  protected Boolean getValue() {
    return myUndefined ? null : getComponent().isSelected();
  }

  @Override
  protected void setValue(final Boolean value) {
    myUndefined = value == null;
    getComponent().setSelected(value != null && value);
  }

}
