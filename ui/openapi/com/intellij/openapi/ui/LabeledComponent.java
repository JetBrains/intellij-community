/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class LabeledComponent<Comp extends JComponent> extends JPanel {
  private final JLabel myLabel = new JLabel();
  private Comp myComponent;
  private String myLabelConstraints = BorderLayout.NORTH;

  public LabeledComponent() {
    super(new BorderLayout());
    insertLabel();
    updateLabelBorder();
    updateUI();
  }

  private void updateLabelBorder() {
    int left = 0;
    int bottom = 0;
    if (BorderLayout.NORTH.equals(myLabelConstraints)) {
      bottom = 2;
    }
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, left, bottom, 0));
  }

  public void updateUI() {
    super.updateUI();
    if (myLabel != null) updateLabelBorder();
  }

  private void insertLabel() {
    remove(myLabel);
    add(myLabel, myLabelConstraints);
  }

  public void setText(String textWithMnemonic) {
    if (!StringUtil.endsWithChar(textWithMnemonic, ':')) textWithMnemonic += ":";
    TextWithMnemonic withMnemonic = TextWithMnemonic.fromTextWithMnemonic(textWithMnemonic);
    withMnemonic.setToLabel(myLabel);
  }

  public String getText() {
    String text = TextWithMnemonic.fromLabel(myLabel).getTextWithMnemonic();
    if (StringUtil.endsWithChar(text, ':')) return text.substring(0, text.length() - 1);
    return text;
  }

  public void setComponentClass(@NonNls String className) throws ClassNotFoundException, InstantiationException,
                                                                                           IllegalAccessException {
    Class<Comp> aClass = (Class<Comp>)getClass().getClassLoader().loadClass(className);
    Comp component = aClass.newInstance();
    setComponent(component);
  }

  public void setComponent(Comp component) {
    if (myComponent != null) remove(myComponent);
    myComponent = component;
    add(myComponent, BorderLayout.CENTER);
    if (myComponent instanceof ComponentWithBrowseButton) {
      myLabel.setLabelFor(((ComponentWithBrowseButton)myComponent).getChildComponent());
    } else myLabel.setLabelFor(myComponent);
  }

  public String getComponentClass() {
    if (myComponent == null) return null;
    return getComponent().getClass().getName();
  }

  public Comp getComponent() {
    return myComponent;
  }

  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (myComponent != null) myComponent.setEnabled(enabled);
    myLabel.setEnabled(enabled);
  }

  public void setLabelLocation(@NonNls String borderConstrains) {
    String constrains = findBorderConstrains(borderConstrains);
    if (constrains == null || constrains == myLabelConstraints) return;
    myLabelConstraints = borderConstrains;
    insertLabel();
  }

  public String getLabelLocation() {
    return myLabelConstraints;
  }

  public Insets getLabelInsets() {
    return myLabel.getInsets();
  }

  public void setLabelInsets(Insets insets) {
    if (Comparing.equal(insets, getLabelInsets())) return;
    myLabel.setBorder(IdeBorderFactory.createEmptyBorder(insets));
  }

  private static final String[] LABEL_BORDER_CONSTRAINS = new String[]{BorderLayout.NORTH, BorderLayout.EAST, BorderLayout.SOUTH, BorderLayout.WEST};

  private static String findBorderConstrains(String str) {
    for (String constrain : LABEL_BORDER_CONSTRAINS) {
      if (constrain.equals(str)) return constrain;
    }
    return null;
  }

  public String getRawText() {
    return myLabel.getText();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public static class TextWithMnemonic {
    private final String myText;
    private final int myMnemoniIndex;

    public TextWithMnemonic(String text, int mnemoniIndex) {
      myText = text;
      myMnemoniIndex = mnemoniIndex;
    }

    public void setToLabel(JLabel label) {
      label.setText(myText);
      if (myMnemoniIndex != -1) label.setDisplayedMnemonic(myText.charAt(myMnemoniIndex));
      else label.setDisplayedMnemonic(0);
      label.setDisplayedMnemonicIndex(myMnemoniIndex);
    }

    public String getTextWithMnemonic() {
      if (myMnemoniIndex == -1) return myText;
      return myText.substring(0, myMnemoniIndex) + "&" + myText.substring(myMnemoniIndex);
    }

    public static TextWithMnemonic fromTextWithMnemonic(String textWithMnemonic) {
      int mnemonicIndex = textWithMnemonic.indexOf('&');
      if (mnemonicIndex == -1) {
        return new TextWithMnemonic(textWithMnemonic, -1);
      }
      textWithMnemonic = textWithMnemonic.substring(0, mnemonicIndex) + textWithMnemonic.substring(mnemonicIndex + 1);
      return new TextWithMnemonic(textWithMnemonic, mnemonicIndex);
    }

    public static TextWithMnemonic fromLabel(JLabel label) {
      return new TextWithMnemonic(label.getText(), label.getDisplayedMnemonicIndex());
    }
  }
}
