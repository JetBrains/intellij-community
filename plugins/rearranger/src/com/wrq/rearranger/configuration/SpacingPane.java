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
package com.wrq.rearranger.configuration;

import com.wrq.rearranger.settings.ForceBlankLineSetting;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;

/** UI code for Spacing rules dialog. */
public class SpacingPane {
// ------------------------------ FIELDS ------------------------------

  private final RearrangerSettings settings;

// --------------------------- CONSTRUCTORS ---------------------------

  public SpacingPane(RearrangerSettings settings) {
    this.settings = settings;
  }

// -------------------------- OTHER METHODS --------------------------

  public JPanel getPane() {
    final JPanel panel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    panel.setBorder(border);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1.0d;
    constraints.insets = new Insets(3, 3, 0, 0);
    panel.add(getForceBlankLinePanel(settings.getAfterClassLBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getBeforeClassRBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getAfterClassRBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getBeforeMethodLBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getAfterMethodLBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getBeforeMethodRBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getAfterMethodRBrace()), constraints.weightedLastCol());
    constraints.newRow();
    panel.add(getForceBlankLinePanel(settings.getNewLinesAtEOF()), constraints.weightedLastCol());
    constraints.weightedLastRow();
    final JCheckBox insideBlockBox = new JCheckBox("Remove initial and final blank lines inside code block");
    insideBlockBox.setSelected(settings.isRemoveBlanksInsideCodeBlocks());
    panel.add(insideBlockBox, constraints.weightedLastCol());
    insideBlockBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        settings.setRemoveBlanksInsideCodeBlocks(insideBlockBox.isSelected());
      }
    });
    return panel;
  }

  private JPanel getForceBlankLinePanel(final ForceBlankLineSetting fbl) {
    final JPanel panel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.lastRow();

    final JCheckBox forceBox = new JCheckBox("Force");
    forceBox.setSelected(fbl.isForce());
    panel.add(forceBox, constraints.weightedFirstCol());
    final NumberFormat integerInstance = NumberFormat.getIntegerInstance();
    integerInstance.setMaximumIntegerDigits(2);
    integerInstance.setMinimumIntegerDigits(1);
    final JFormattedTextField nBlankLines = new JFormattedTextField(integerInstance);
    nBlankLines.setValue(new Integer("88"));
    Dimension d = nBlankLines.getPreferredSize();
    d.width += 3;
    nBlankLines.setPreferredSize(d);
    nBlankLines.setValue(fbl.getnBlankLines());
    nBlankLines.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
    constraints.insets = new Insets(0, 3, 0, 0);
    panel.add(nBlankLines, constraints.weightedNextCol());
    nBlankLines.addPropertyChangeListener("value", new PropertyChangeListener() {
      public void propertyChange(final PropertyChangeEvent evt) {
        int n = ((Number)nBlankLines.getValue()).intValue();
        if (n < 0) {
          n = 0;
          nBlankLines.setValue(n);
        }
        fbl.setnBlankLines(n);
      }
    });
    String labelText = "blank lines " +
                       (fbl.isBefore() ? "before" : "after");
    switch (fbl.getObject()) {
      case ForceBlankLineSetting.CLASS_OBJECT:
        labelText += " class ";
        break;
      case ForceBlankLineSetting.METHOD_OBJECT:
        labelText += " method ";
        break;
    }
    labelText += fbl.isOpenBrace() ? "open brace \"{\""
                                   : "close brace \"}\"";
    if (fbl.getObject() == ForceBlankLineSetting.EOF_OBJECT) {
      labelText = "newline characters at end of file";
    }
    final JLabel blankLineLabel = new JLabel(labelText);
    panel.add(blankLineLabel, constraints.lastCol());
    nBlankLines.setEnabled(forceBox.isSelected());
    blankLineLabel.setEnabled(forceBox.isSelected());
    forceBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        fbl.setForce(forceBox.isSelected());
        nBlankLines.setEnabled(forceBox.isSelected());
        blankLineLabel.setEnabled(forceBox.isSelected());
      }
    });
    return panel;
  }
}
