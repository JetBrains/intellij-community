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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.wrq.rearranger.configuration.IntTextField;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import com.wrq.rearranger.util.MethodUtil;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Allow item selection by comparison with an integer attribute. */
abstract public class IntegerAttribute extends AtomicAttribute {
  public static final int OP_GE = 0;
  public static final int OP_LE = 1;

  // ------------------------------ FIELDS ------------------------------
  protected     int     value;
  protected     int     opType;
  protected     boolean match;                // when true, condition applies
  private final String  attributeDisplayName; // should be plural
  private final String  storageName;

// --------------------------- CONSTRUCTORS ---------------------------

  IntegerAttribute(final String attributeDisplayName,
                   final String storageName,
                   final int opType)
  {
    this.attributeDisplayName = attributeDisplayName;
    this.storageName = storageName;
    this.opType = opType;
  }

  // --------------------- GETTER / SETTER METHODS ---------------------
  public int getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

  public int getOpType() {
    return opType;
  }

  public final boolean isMatch() {
    return match;
  }

  public final void setMatch(final boolean match) {
    this.match = match;
  }

  public final JPanel getIntegerPanel() {
    final JPanel intPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    constraints.weightedLastRow();
    final JCheckBox enableBox = new JCheckBox(opType == OP_GE ? "with at least" : "with at most");
    intPanel.add(enableBox, constraints.firstCol());
    final IntTextField intField = new IntTextField(
      new IntTextField.IGetSet() {
        public int get() {
          return value;
        }

        public void set(int v) {
          value = v;
        }
      },
      0, 99);

    constraints.insets = new Insets(0, 3, 0, 0);
    intPanel.add(intField, constraints.nextCol());
    final JLabel attribLabel = new JLabel(attributeDisplayName + "s");
    intPanel.add(attribLabel, constraints.weightedLastCol());
    enableBox.setSelected(match);
    enableBox.setForeground(enableBox.isSelected() ? Color.BLACK : Color.GRAY);
    intField.setEnabled(match);
    attribLabel.setEnabled(match);
    enableBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setMatch(enableBox.isSelected());
        intPanel.setEnabled(enableBox.isSelected());
        enableBox.setSelected(match);
        enableBox.setForeground(enableBox.isSelected() ? Color.BLACK : Color.GRAY);
        intField.setEnabled(match);
        attribLabel.setEnabled(match);
      }
    });
    return intPanel;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(final Object obj) {
    if (!(obj instanceof IntegerAttribute)) return false;
    final IntegerAttribute na = (IntegerAttribute)obj;
    return match == na.match &&
           opType == na.opType &&
           value == na.value;
  }

// -------------------------- OTHER METHODS --------------------------

  public final void appendAttributes(final Element me) {
    final Element stElement = new Element(storageName);

    me.getChildren().add(stElement);
    stElement.setAttribute("match", Boolean.valueOf(match).toString());
    stElement.setAttribute("opType", "" + opType);
    stElement.setAttribute("value", "" + value);
  }

  protected final void deepCopy(IntegerAttribute result) {
    result.match = match;
    result.opType = opType;
    result.value = value;
  }

  public final String getDescriptiveString() {
    return (match ?
            (opType == OP_GE ? "with at least " : "with at most ") +
            value + " " + attributeDisplayName + (value != 1 ? "s" : "")
                  : "");
  }

  public final boolean isMatch(PsiElement value) {
    if (!match) return true;
    if (!(value instanceof PsiMethod)) return false;
    PsiMethod method = (PsiMethod)value;
    int n = MethodUtil.nofParameters(method);
    if (opType == OP_GE) {
      return n >= this.value;
    }
    else {
      return n <= this.value;
    }
  }

  public final void loadAttributes(final Element item) {
    match = RearrangerSettings.getBooleanAttribute(item, "match", false);
    //    opType = RearrangerSettings.getIntAttribute(item, "opType", opType);   -- never load opType, it is immutable
    value = RearrangerSettings.getIntAttribute(item, "value", 0);
  }
}
