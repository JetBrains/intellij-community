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

import com.intellij.util.xml.NamedEnumUtil;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author peter
 */
public class BooleanEnumControl extends BaseModifiableControl<JCheckBox, String> {
  private boolean myUndefined;
  private final String mySelectedValue;
  private final String myUnselectedValue;

  public BooleanEnumControl(final DomWrapper<String> domWrapper, String selectedValue, String unselectedValue) {
    super(domWrapper);
    mySelectedValue = selectedValue;
    myUnselectedValue = unselectedValue;
  }

  public BooleanEnumControl(final DomWrapper<String> domWrapper, Class<? extends Enum> enumClass, boolean invertedOrder) {
    this(domWrapper, NamedEnumUtil.getEnumValueByElement(enumClass.getEnumConstants()[invertedOrder ? 0 : 1]), NamedEnumUtil.getEnumValueByElement(enumClass.getEnumConstants()[invertedOrder ? 1 : 0]));
    assert enumClass.getEnumConstants().length == 2 : enumClass;
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
  protected String getValue() {
    return myUndefined ? null : (getComponent().isSelected() ? mySelectedValue : myUnselectedValue);
  }

  @Override
  protected void setValue(final String value) {
    myUndefined = value == null;
    getComponent().setSelected(mySelectedValue.equals(value));
  }

}
