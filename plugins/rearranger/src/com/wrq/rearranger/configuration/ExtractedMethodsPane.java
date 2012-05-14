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

import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RelatedMethodsSettings;
import com.wrq.rearranger.util.Constraints;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Configuration settings for handling ordering of extracted methods. */
public class ExtractedMethodsPane {
// --------------------------- main() method ---------------------------


// ---------- START Level 1 ----------


// ---------- START Level 2 ----------

  JPanel getExtractedMethodsPane(final RelatedMethodsSettings rms) {
    final JPanel containerPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    final JTabbedPane pane = new JTabbedPane();
    final JPanel relatedMethodsArrangementPane = getExtractedMethodsArrangementPane(rms);
    final JPanel relatedMethodsCommentsPane = getRelatedMethodsCommentsPane(rms);
    pane.addTab("Arrangement", null, relatedMethodsArrangementPane,
                "Options controlling arrangement of extracted methods");
    pane.addTab("Comments", null, relatedMethodsCommentsPane,
                "Extracted method comments");
    pane.setSelectedIndex(0);
    containerPanel.add(pane, constraints.weightedLastCol());
    return containerPanel;
  }

// ---------- START Level 3 ----------


  private JPanel getExtractedMethodsArrangementPane(final RelatedMethodsSettings rms) {
    final JPanel pane = new JPanel(new GridBagLayout());
    final JPanel innerPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    final TitledBorder tb = new TitledBorder(border, "Options controlling arrangement of extracted methods");
    innerPanel.setBorder(tb);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    JPanel traversalOrderPanel = getTraversalOrderPanel(rms);
    JPanel orderingPanel = getOrderingPanel(rms);
    JPanel extractedPanel = getExtractedPanel(rms);
    innerPanel.add(getMoveMethodsPanel(rms, new JPanel[]{traversalOrderPanel, orderingPanel, extractedPanel}),
                   constraints.weightedLastCol());
    constraints.newRow();
    constraints.insets = new Insets(0, 20, 0, 0);
    innerPanel.add(traversalOrderPanel, constraints.weightedLastCol());
    constraints.newRow();
    innerPanel.add(orderingPanel, constraints.weightedLastCol());
    constraints.weightedLastRow();
    innerPanel.add(extractedPanel, constraints);
    Dimension d = innerPanel.getPreferredSize();
    innerPanel.setPreferredSize(d);
    innerPanel.setMaximumSize(d);
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx = constraints.gridy = 0;
    pane.add(innerPanel, constraints);
    return pane;
  }

// ---------- START Level 4 ----------


  JPanel getTraversalOrderPanel(final RelatedMethodsSettings rms) {
    final JPanel traversalOrderPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    {
      constraints.insets = new Insets(5, 5, 5, 0);
      JLabel performLabel = new JLabel("Perform");
      traversalOrderPanel.add(performLabel, constraints.firstCol());
    }
    {
      final JComboBox traversalOrderBox = new JComboBox(new String[]{"depth first", "breadth first"});
      traversalOrderPanel.add(traversalOrderBox, constraints.nextCol());
      traversalOrderBox.setSelectedIndex(1);
      Dimension d = traversalOrderBox.getPreferredSize();
      d.width += 3;
      traversalOrderBox.setPreferredSize(d);
      traversalOrderBox.setSelectedIndex(rms.isDepthFirstOrdering() ? 0 : 1);
      traversalOrderBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          rms.setDepthFirstOrdering(traversalOrderBox.getSelectedIndex() == 0);
        }
      });
    }
    {
      JLabel remainderLabel = new JLabel("ordering on nested method calls");
      traversalOrderPanel.add(remainderLabel, constraints.lastCol());
    }
    return traversalOrderPanel;
  }

  JPanel getOrderingPanel(final RelatedMethodsSettings rms) {
    final JPanel orderingPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    final TitledBorder tb = new TitledBorder(border, "Ordering options");
    orderingPanel.setBorder(tb);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    final JRadioButton invocationButton = new JRadioButton("Place in order of invocation");
    final JRadioButton originalButton = new JRadioButton("Retain original order");
    final JRadioButton alphabetizeButton = new JRadioButton("Alphabetize");
    ButtonGroup group = new ButtonGroup();
    group.add(invocationButton);
    group.add(originalButton);
    group.add(alphabetizeButton);
    orderingPanel.add(invocationButton, constraints.weightedLastCol());
    constraints.newRow();
    orderingPanel.add(originalButton, constraints.weightedLastCol());
    constraints.weightedLastRow();
    orderingPanel.add(alphabetizeButton, constraints.weightedLastCol());
    switch (rms.getOrdering()) {
      case RelatedMethodsSettings.INVOCATION_ORDER:
        invocationButton.setSelected(true);
        break;
      case RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER:
        originalButton.setSelected(true);
        break;
      case RelatedMethodsSettings.ALPHABETICAL_ORDER:
        alphabetizeButton.setSelected(true);
        break;
    }

    invocationButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (invocationButton.isSelected()) {
          rms.setOrdering(RelatedMethodsSettings.INVOCATION_ORDER);
        }
      }
    });

    originalButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (originalButton.isSelected()) {
          rms.setOrdering(RelatedMethodsSettings.RETAIN_ORIGINAL_ORDER);
        }
      }
    });

    alphabetizeButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (alphabetizeButton.isSelected()) {
          rms.setOrdering(RelatedMethodsSettings.ALPHABETICAL_ORDER);
        }
      }
    });
    return orderingPanel;
  }

  JPanel getExtractedPanel(final RelatedMethodsSettings rms) {
    final JPanel extractedPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.insets = new Insets(5, 5, 5, 0);
    JLabel label = new JLabel("Consider a non-private method as an extracted method:");
    extractedPanel.add(label, constraints.weightedLastCol());
    constraints.insets = new Insets(0, 15, 0, 0);
    final JRadioButton neverButton = new JRadioButton("never");
    constraints.newRow();
    extractedPanel.add(neverButton, constraints.weightedLastCol());
    final JRadioButton oneButton = new JRadioButton("when it is called by only one method in the class");
    constraints.newRow();
    extractedPanel.add(oneButton, constraints.weightedLastCol());
    final JRadioButton moreButton = new JRadioButton("when it has one or more callers in the class");
    constraints.weightedLastRow();
    extractedPanel.add(moreButton, constraints.weightedLastCol());
    ButtonGroup g = new ButtonGroup();
    g.add(neverButton);
    g.add(oneButton);
    g.add(moreButton);
    switch (rms.getNonPrivateTreatment()) {
      case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_NEVER:
        neverButton.setSelected(true);
        break;
      case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ONE_CALLER:
        oneButton.setSelected(true);
        break;
      case RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ANY_CALLERS:
        moreButton.setSelected(true);
        break;
    }
    neverButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (neverButton.isSelected()) {
          rms.setNonPrivateTreatment(RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_NEVER);
        }
      }
    });
    oneButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (oneButton.isSelected()) {
          rms.setNonPrivateTreatment(RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ONE_CALLER);
        }
      }
    });
    moreButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (moreButton.isSelected()) {
          rms.setNonPrivateTreatment(RelatedMethodsSettings.NON_PRIVATE_EXTRACTED_ANY_CALLERS);
        }
      }
    });
    return extractedPanel;
  }

  JPanel getMoveMethodsPanel(final RelatedMethodsSettings rms, final JPanel[] dependentPanels) {
    final JPanel moveMethodsPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.weightedLastRow();
    {
      constraints.insets = new Insets(5, 5, 5, 0);
      final JCheckBox moveBox = new JCheckBox("Move extracted methods below");
      moveBox.setSelected(rms.isMoveExtractedMethods());
      moveBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          rms.setMoveExtractedMethods(moveBox.isSelected());
          enableComponents(moveMethodsPanel, moveBox.isSelected());
          enableOtherComponents(dependentPanels, moveBox.isSelected());
        }
      });
      moveMethodsPanel.add(moveBox, constraints.firstCol());
    }
    {
      final JComboBox firstLastBox = new JComboBox(new String[]{"first", "last"});
      firstLastBox.setSelectedIndex(0);
      Dimension d = firstLastBox.getPreferredSize();
      d.width += 3;
      firstLastBox.setPreferredSize(d);
      firstLastBox.setSelectedIndex(rms.isBelowFirstCaller() ? 0 : 1);
      firstLastBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          rms.setBelowFirstCaller(firstLastBox.getSelectedIndex() == 0);
        }
      });
      moveMethodsPanel.add(firstLastBox, constraints.nextCol());
    }
    {
      constraints.insets = new Insets(8, 5, 5, 0);
      JLabel usageLabel = new JLabel("usage");
      moveMethodsPanel.add(usageLabel, constraints.weightedLastCol());
    }
    enableComponents(moveMethodsPanel, rms.isMoveExtractedMethods());
    enableOtherComponents(dependentPanels, rms.isMoveExtractedMethods());
    return moveMethodsPanel;
  }

// ---------- START Level 5 ----------


  private void enableComponents(JPanel pane, boolean selected) {
    for (int i = 1; i < pane.getComponents().length; i++) {
      pane.getComponents()[i].setEnabled(selected);
    }
  }

  private void enableOtherComponents(JPanel[] otherPanels, boolean selected) {
    for (JPanel otherPanel : otherPanels) {
      enablePanel(otherPanel, selected);
    }
  }

// ---------- START Level 6 ----------


  private void enablePanel(JPanel otherPanel, boolean selected) {
    for (int i = 0; i < otherPanel.getComponents().length; i++) {
      Component c = otherPanel.getComponents()[i];
      if (c instanceof JPanel) {
        enablePanel((JPanel)c, selected);
      }
      else {
        c.setEnabled(selected);
      }
    }
  }

// ---------- END Level 6 ----------


// ---------- END Level 5 ----------


// ---------- END Level 4 ----------


  private JPanel getRelatedMethodsCommentsPane(final RelatedMethodsSettings rms) {
    JPanel rmcPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 0.0d;
    constraints.weighty = 0.0d;
    JLabel commentTypeLabel = new JLabel("Emit comment at");
    final JComboBox commentType = new JComboBox(new String[]{
      "top level",
      "each level",
      "each method",
      "family change (up level, or next sibling has children)"
    });
    commentType.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        rms.setCommentType(commentType.getSelectedIndex());
      }
    });
    commentType.setSelectedIndex(rms.getCommentType());
    JLabel legendLabel = new JLabel("Comment Substitutions: use");
    JLabel legendLabel_1 = new JLabel("%TL% for top level method");
    JLabel legendLabel_2 = new JLabel("%MN% for method name");
    JLabel legendLabel_4 = new JLabel("%LV% for level number");
    JLabel legendLabel_3 = new JLabel("%AM% for all method names");
    JLabel precedingCommentLabel = new JLabel("Preceding Comment:");
    JLabel trailingCommentLabel = new JLabel("Trailing Comment:");
    JPanel precedingCommentPanel = getCommentAreaPanel(rms.getPrecedingComment());
    JPanel trailingCommentPanel = getCommentAreaPanel(rms.getTrailingComment());
    constraints.insets = new Insets(3, 5, 0, 0);
    rmcPanel.add(commentTypeLabel, constraints.firstCol());
    rmcPanel.add(commentType, constraints.lastCol());
    JPanel legendPanel = new JPanel(new GridBagLayout());
    {
      final Border border = BorderFactory.createEtchedBorder();
      legendPanel.setBorder(border);
      final Constraints legendConstraints = new Constraints(GridBagConstraints.NORTHWEST);
      legendConstraints.fill = GridBagConstraints.BOTH;

      legendConstraints.insets = new Insets(5, 5, 0, 0);
      legendPanel.add(legendLabel, legendConstraints.firstCol());
      legendConstraints.insets = new Insets(5, 5, 0, 5);
      legendPanel.add(legendLabel_1, legendConstraints.nextCol());
      legendPanel.add(legendLabel_2, legendConstraints.lastCol());
      legendConstraints.newRow();
      legendConstraints.firstCol(); // skip first column
      legendConstraints.insets = new Insets(5, 5, 5, 5);
      legendPanel.add(legendLabel_3, legendConstraints.nextCol());
      legendPanel.add(legendLabel_4, legendConstraints.lastCol());
    }
    constraints.newRow();
    constraints.insets = new Insets(5, 5, 0, 5);
    rmcPanel.add(legendPanel, constraints.weightedLastCol());
    constraints.newRow();
    rmcPanel.add(precedingCommentLabel, constraints.weightedLastCol());
    constraints.weightedNewRow(0.50d);
    rmcPanel.add(precedingCommentPanel, constraints.weightedLastCol());
    constraints.newRow();
    rmcPanel.add(trailingCommentLabel, constraints.weightedLastCol());
    constraints.weightedNewRow(0.50d);
    constraints.insets = new Insets(5, 5, 5, 5);
    rmcPanel.add(trailingCommentPanel, constraints.weightedLastCol());
    constraints.lastRow();
    rmcPanel.add(rms.getCommentFillString().getCommentFillStringPanel(), constraints.weightedLastCol());
    return rmcPanel;
  }

// ---------- START Level 4 ----------


  private JPanel getCommentAreaPanel(final CommentRule comment) {
    JPanel commentPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    final JTextArea commentArea = new JTextArea(4, 40);
    commentArea.setText(comment.getCommentText());
    final JScrollPane scrollPane = new JScrollPane(commentArea);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    commentPanel.add(scrollPane, constraints.weightedFirstCol());
    commentArea.getDocument().addDocumentListener(new DocumentListener() {
      public void changedUpdate(final DocumentEvent e) {
        comment.setCommentText(commentArea.getText());
      }

      public void insertUpdate(final DocumentEvent e) {
        comment.setCommentText(commentArea.getText());
      }

      public void removeUpdate(final DocumentEvent e) {
        comment.setCommentText(commentArea.getText());
      }
    });
    return commentPanel;
  }

// ---------- END Level 4 ----------


// ---------- END Level 3 ----------


// ---------- END Level 2 ----------


// ---------- END Level 1 ----------
}
