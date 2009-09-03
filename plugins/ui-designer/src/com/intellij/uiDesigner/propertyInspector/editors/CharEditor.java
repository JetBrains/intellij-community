package com.intellij.uiDesigner.propertyInspector.editors;

/**
 * @author yole
 */
public class CharEditor extends AbstractTextFieldEditor<Character> {
  public Character getValue() throws Exception {
    final String text = myTf.getText();
    if (text.length() == 0) {
      return null;
    }
    return text.charAt(0);
  }
}