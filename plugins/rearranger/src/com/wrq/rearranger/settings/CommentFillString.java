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
package com.wrq.rearranger.settings;

import com.wrq.rearranger.RearrangerActionHandler;
import com.wrq.rearranger.configuration.IntTextField;
import com.wrq.rearranger.settings.attributeGroups.RegexUtil;
import com.wrq.rearranger.util.CommentUtil;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Attribute;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Implements routines for handling comment fill string expansion. */
public class CommentFillString {
  private String  fillString;                                // replicate in place of %FS%
  private boolean useProjectWidthForFill;
  private int     fillWidth;

  public CommentFillString() {
    fillString = "";
    useProjectWidthForFill = true;
    fillWidth = 0;
  }

  public final String getExpandedCommentText(String commentText) {
    int width = useProjectWidthForFill ? RearrangerActionHandler.getRightMargin() : fillWidth;
    int tabSize = RearrangerActionHandler.getTabSize();
    return CommentUtil.expandFill(commentText, width, tabSize, fillString);
  }

  public String getFillString() {
    return fillString;
  }

  public void setFillString(String fillString) {
    this.fillString = fillString;
  }

  public final boolean equals(final Object object) {
    if (!(object instanceof CommentFillString)) return false;
    final CommentFillString c = (CommentFillString)object;
    return fillString.equals(c.fillString) &&
           useProjectWidthForFill == c.useProjectWidthForFill &&
           fillWidth == c.fillWidth;
  }

  public final CommentFillString deepCopy() {
    final CommentFillString comment = new CommentFillString();
    comment.fillString = fillString;
    comment.useProjectWidthForFill = useProjectWidthForFill;
    comment.fillWidth = fillWidth;
    return comment;
  }

  public static CommentFillString readExternal(final Element item) {
    final CommentFillString result = new CommentFillString();
    Attribute fillAttr = RearrangerSettings.getAttribute(item, "fillString");
    if (fillAttr == null) {
      result.fillString = "";
    }
    else {
      String fill = fillAttr.getValue();
      result.fillString = CommentRule.unescape(fill);
    }
    result.useProjectWidthForFill = RearrangerSettings.getBooleanAttribute(item, "useProjectWidthForFill", true);
    result.fillWidth = RearrangerSettings.getIntAttribute(item, "fillWidth", 0);
    return result;
  }

  public final void writeExternal(final Element me) {
    me.setAttribute("fillString", CommentRule.escape(fillString));
    me.setAttribute("useProjectWidthForFill", String.valueOf(useProjectWidthForFill));
    me.setAttribute("fillWidth", String.valueOf(fillWidth));
  }

  public final JPanel getCommentFillStringPanel() {
    final JPanel cfsPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.insets = new Insets(4, 4, 4, 0);
    constraints.newRow();
    final JLabel fillStringLabel = new JLabel("Fill string:");
    fillStringLabel.setToolTipText("Occurrences of %FS% in the comment separator text will be replaced with equal \n" +
                                   "amounts of the fill string, replicated to make the comment end at the specified column");
    cfsPanel.add(fillStringLabel, constraints.firstCol());
    final JTextField fillString = new JTextField(this.fillString);
    cfsPanel.add(fillString, constraints.weightedLastCol());
    constraints.newRow();
    final JCheckBox useProjectWidthBox = new JCheckBox("Use project right margin as fill width");
    useProjectWidthBox.setSelected(useProjectWidthForFill);
    cfsPanel.add(useProjectWidthBox, constraints.weightedLastCol());
    constraints.lastRow();
    constraints.insets = new Insets(4, 15, 4, 4);
    final JLabel fillWidthLabel = new JLabel("Fill width:");
    cfsPanel.add(fillWidthLabel, constraints.firstCol());
    final IntTextField fillWidthField = new IntTextField(
      new IntTextField.IGetSet() {
        public int get() {
          return fillWidth;
        }

        public void set(int value) {
          fillWidth = value;
        }
      },
      0,
      999
    );
    cfsPanel.add(fillWidthField, constraints.nextCol());
    cfsPanel.add(new JLabel(), constraints.weightedLastCol());
    fillWidthLabel.setEnabled(!useProjectWidthForFill);
    fillWidthField.setEnabled(!useProjectWidthForFill);
    fillString.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        setFillString(fillString.getText());
      }

      public void removeUpdate(DocumentEvent e) {
        setFillString(fillString.getText());
      }

      public void changedUpdate(DocumentEvent e) {
        setFillString(fillString.getText());
      }
    });
    useProjectWidthBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        useProjectWidthForFill = useProjectWidthBox.isSelected();
        fillWidthLabel.setEnabled(!useProjectWidthForFill);
        fillWidthField.setEnabled(!useProjectWidthForFill);
      }
    });
    return cfsPanel;
  }

  /** Returns a regular expression that will match the fill string. */
  public String getFillStringPattern() {
    String fs = fillString;
    if (fs.length() == 0) fs = " ";
    final int length = fs.length();
    StringBuilder sb = new StringBuilder(length * (length + 1) / 2 + length * 3);
    sb.append('(');
    sb.append(RegexUtil.escape(fs));
    sb.append(")*");
    if (length > 1) {
      sb.append('(');
      for (int i = length - 1; i > 0; i--) {
        sb.append(RegexUtil.escape(fs.substring(0, i)));
        sb.append(i <= 1 ? ")?" : "|");
      }
    }
    return sb.toString();
  }
}
