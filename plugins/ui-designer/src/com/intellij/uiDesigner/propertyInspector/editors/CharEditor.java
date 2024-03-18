// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors;


public class CharEditor extends AbstractTextFieldEditor<Character> {
  @Override
  public Character getValue() throws Exception {
    final String text = myTf.getText();
    if (text.isEmpty()) {
      return null;
    }
    return text.charAt(0);
  }
}