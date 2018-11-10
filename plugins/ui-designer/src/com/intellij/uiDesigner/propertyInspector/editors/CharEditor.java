// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.propertyInspector.editors;

/**
 * @author yole
 */
public class CharEditor extends AbstractTextFieldEditor<Character> {
  @Override
  public Character getValue() throws Exception {
    final String text = myTf.getText();
    if (text.length() == 0) {
      return null;
    }
    return text.charAt(0);
  }
}