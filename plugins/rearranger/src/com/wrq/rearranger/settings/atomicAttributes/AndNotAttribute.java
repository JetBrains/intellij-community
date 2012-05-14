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
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Base class for all boolean attributes. */
abstract public class AndNotAttribute
  extends AtomicAttribute
{
// ------------------------------ FIELDS ------------------------------

  boolean value;
  boolean invert;
  private final String valueName;
  private final int    modifierBit;

// --------------------------- CONSTRUCTORS ---------------------------

  AndNotAttribute(final String s, final int modifierBit) {
    valueName = s;
    this.modifierBit = modifierBit;
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public final JPanel getAndNotPanel() {
    final JPanel andNotPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.WEST);
    final JCheckBox andBox = new JCheckBox("And");
    andBox.setSelected(isValue());
    final JCheckBox notBox = new JCheckBox("not");
    notBox.setSelected(isInvert());
    notBox.setEnabled(andBox.isSelected());
    notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
    final JLabel nameLabel = new JLabel(getName());
    nameLabel.setEnabled(andBox.isSelected());
    andNotPanel.add(andBox, constraints.firstCol());
    andNotPanel.add(notBox, constraints.nextCol());
    constraints.insets = new Insets(0, 0, 0, 4);
    andNotPanel.add(nameLabel, constraints.weightedLastCol());
    andBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setValue(andBox.isSelected());
        notBox.setEnabled(andBox.isSelected());
        nameLabel.setEnabled(andBox.isSelected());
      }
    });
    notBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setInvert(notBox.isSelected());
        notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
      }
    });
    return andNotPanel;
  }

  private String getName() {
    return valueName;
  }

  public final boolean isInvert() {
    return invert;
  }

  public final void setInvert(final boolean invert) {
    this.invert = invert;
  }

  public final boolean isValue() {
    return value;
  }

  public final void setValue(final boolean value) {
    this.value = value;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public final boolean equals(final Object obj) {
    if (!(obj instanceof AndNotAttribute)) return false;
    final AndNotAttribute ana = (AndNotAttribute)obj;
    return (value == ana.value &&
            invert == ana.invert &&
            valueName.equals(ana.valueName) &&
            modifierBit == ana.modifierBit);
  }

// -------------------------- OTHER METHODS --------------------------

  public final void appendAttributes(final Element me) {
    final Element stElement = new Element(getElementName());
    me.getChildren().add(stElement);
    stElement.setAttribute("value", Boolean.valueOf(value).toString());
    stElement.setAttribute("invert", Boolean.valueOf(invert).toString());
  }

  final String getElementName() {
    final StringBuffer sb = new StringBuffer(valueName.length());
    sb.append(valueName);
    sb.replace(0, 1, new String("" + sb.charAt(0)).toUpperCase());
    for (int i = 1; i < sb.length(); i++) {
      if (sb.charAt(i) == ' ') {
        sb.deleteCharAt(i);
        sb.replace(i, i + 1, new String("" + sb.charAt(i)).toUpperCase());
      }
    }
    return sb.toString();
  }

  public final String getDescriptiveString() {
    return (isValue() ?
            isInvert() ? "non-" + valueName + " " : valueName + " "
                      : "");
  }

  public final boolean isMatch(final int modifiers) {
    return (!isValue()) ? true : ((modifiers & modifierBit) > 0) ^ isInvert();
  }

  public final void loadAttributes(final Element item) {
    value = RearrangerSettings.getBooleanAttribute(item, "value");
    invert = RearrangerSettings.getBooleanAttribute(item, "invert");
  }
}

