/*
 * Copyright (c) 2003, 2010, Dave Kriewall
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.wrq.rearranger.settings.atomicAttributes;

import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Attribute;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Allows item selection by matching a string attribute to a regular expression. */
abstract public class StringAttribute extends AtomicAttribute {

// ------------------------------ FIELDS ------------------------------

  private       String  expression;
  private       boolean match;
  private       boolean invert;
  private final String  attributeDisplayName;  // should be plural
  private final String  storageName;

// --------------------------- CONSTRUCTORS ---------------------------

  StringAttribute(String attributeDisplayName, String storageName) {
    this.attributeDisplayName = attributeDisplayName;
    this.storageName = storageName;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public final String getExpression() {
    return expression;
  }

  public final void setExpression(final String expression) {
    this.expression = expression;
  }

  public final JPanel getStringPanel() {
    final JPanel stringPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    constraints.weightedLastRow();
    final JCheckBox enableBox = new JCheckBox("whose " + attributeDisplayName);
    stringPanel.add(enableBox, constraints.firstCol());
    final JComboBox invertBox = new JComboBox(new Object[]{"match", "do not match"});
    invertBox.setSelectedIndex(isInvert() ? 1 : 0);
    final JTextField patternField = new JTextField(20);
    stringPanel.add(invertBox, constraints.nextCol());
    stringPanel.add(patternField, constraints.weightedLastCol());
    enableBox.setSelected(isMatch());
    invertBox.setEnabled(isMatch());
    patternField.setEnabled(isMatch());
    patternField.setText(getExpression());
    enableBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setMatch(enableBox.isSelected());
        invertBox.setEnabled(enableBox.isSelected());
        patternField.setEnabled(enableBox.isSelected());
      }
    });
    invertBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setInvert(invertBox.getSelectedIndex() == 1);
      }
    });
    patternField.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(final DocumentEvent e) {
        setExpression(patternField.getText());
      }

      public void insertUpdate(final DocumentEvent e) {
        setExpression(patternField.getText());
      }

      public void removeUpdate(final DocumentEvent e) {
        setExpression(patternField.getText());
      }
    });
    return stringPanel;
  }

  public final boolean isInvert() {
    return invert;
  }

  private void setInvert(final boolean invert) {
    this.invert = invert;
  }

  public final boolean isMatch() {
    return match;
  }

  public final void setMatch(final boolean match) {
    this.match = match;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(final Object object) {
    if (!(object instanceof StringAttribute)) return false;
    final StringAttribute na = (StringAttribute)object;
    return match == na.match &&
           (expression == null || na.expression == null ?
            expression == null && na.expression == null : expression.equals(na.expression)) &&
           invert == na.invert;
  }

// -------------------------- OTHER METHODS --------------------------

  public final void appendAttributes(final Element me) {
    final Element stElement = new Element(storageName);
    if (expression == null) {
      expression = "";
    }
    me.getChildren().add(stElement);
    stElement.setAttribute("match", Boolean.valueOf(match).toString());
    stElement.setAttribute("invert", Boolean.valueOf(invert).toString());
    stElement.setAttribute("pattern", expression);
  }

  protected final void deepCopy(StringAttribute result) {
    result.match = match;
    result.expression = expression;
    result.invert = invert;
  }

  public final String getDescriptiveString() {
    return (match ? "whose " +
                    attributeDisplayName +
                    (invert ? " do not match '" : " match '") +
                    expression + "'"
                  : "");
  }

  public final boolean isMatch(final String string) {
    return !match || string.matches(expression) ^ invert;
  }

  public final void loadAttributes(final Element item) {
    match = RearrangerSettings.getBooleanAttribute(item, "match", false);
    invert = RearrangerSettings.getBooleanAttribute(item, "invert", false);
    final Attribute attr = RearrangerSettings.getAttribute(item, "pattern");
    expression = (attr == null ? "" : ((java.lang.String)attr.getValue()));
  }
}
