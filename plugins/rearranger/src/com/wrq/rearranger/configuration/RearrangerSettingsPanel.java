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

import com.wrq.rearranger.Rearranger;
import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.attributeGroups.AttributeGroup;
import com.wrq.rearranger.settings.attributeGroups.RegexUtil;
import com.wrq.rearranger.util.CommentUtil;
import com.wrq.rearranger.util.Constraints;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/** Code for top level configuration panel. */
public final class RearrangerSettingsPanel
  extends JPanel
{
// ------------------------------ FIELDS ------------------------------

  public RearrangerSettings settings;

// --------------------------- CONSTRUCTORS ---------------------------

  // ---------- START Level 1 ----------
  public RearrangerSettingsPanel(final RearrangerSettings externalSettings) {
    RearrangerSettings settings = externalSettings.deepCopy();
    setLayout(new GridBagLayout());
    createSettingsPanel(settings);
  }

// ---------- START Level 2 ----------

  private void createSettingsPanel(RearrangerSettings settings) {
    this.settings = settings;
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    while (getComponents().length > 0) {
      remove(0);
    }
    add(getSettingsPanel(), constraints.weightedLastCol());
    validate();
  }

// ---------- START Level 3 ----------

  private JPanel getSettingsPanel() {
    final JPanel containerPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightedLastRow();
    final JTabbedPane pane = new JTabbedPane();
    final SettingsPane classMemberSettingsPane = new ClassMemberSettingsPane(settings);
    final SettingsPane classOrderSettingsPane = new ClassOrderSettingsPane(settings);
    final SpacingPane spacingPane = new SpacingPane(settings);
    final ExtractedMethodsPane extractedMethodsSettings = new ExtractedMethodsPane();
    final JPanel generalSettingsPane = getGeneralSettingsPane();
    final RelatedByNamePane rbnp = new RelatedByNamePane(settings.getExtractedMethodsSettings().getRbnms());
    pane.addTab(
      "Class Member Order",
      null,
      classMemberSettingsPane.getPane(),
      "Options controlling arrangement of fields, methods and inner classes"
    );
    pane.addTab(
      "Outer Class Order",
      null,
      classOrderSettingsPane.getPane(),
      "Options controlling arrangement of outer classes"
    );
    pane.addTab(
      "Extracted Methods",
      null,
      extractedMethodsSettings.getExtractedMethodsPane(settings.getExtractedMethodsSettings()),
      "Options controlling arrangement of extracted methods"
    );
    pane.addTab(
      "General",
      null,
      generalSettingsPane,
      "General options"
    );
    pane.addTab(
      "Spacing",
      null,
      spacingPane.getPane(),
      "Options controlling number of blank lines before and after braces"
    );
    pane.addTab(
      "Configuration",
      null,
      getConfigurationPane(),
      "Load, save, clear configuration, or set to default"
    );
//        pane.addTab("Related By Name", null,  // todo - add when ready; replace "Extracted Methods" pane above
//                rbnp.getPane(), "Global definitions for methods related by name (\"getters/setters\")");
    pane.setSelectedIndex(0);
    containerPanel.add(pane, constraints.weightedLastCol());
    return containerPanel;
  }

// ---------- START Level 4 ----------

  private JPanel getGeneralSettingsPane() {
    final JPanel gsPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    gsPanel.setBorder(border);
    final GridBagConstraints constraints = new GridBagConstraints();

    constraints.fill = GridBagConstraints.BOTH;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 0;
    constraints.weighty = 0;
    constraints.gridx = constraints.gridy = 0;
    constraints.insets = new Insets(5, 5, 0, 0);
    final JCheckBox getterSetterBox = new JCheckBox("Keep getters/setters together");
    getterSetterBox.setSelected(settings.isKeepGettersSettersTogether());
    final JCheckBox gettersSettersWithPropertyBox = new JCheckBox("Keep getters/setters with property");
    gettersSettersWithPropertyBox.setSelected(settings.isKeepGettersSettersWithProperty());
    gettersSettersWithPropertyBox.setEnabled(settings.isKeepGettersSettersTogether());
    final JCheckBox overloadedMethodsBox = new JCheckBox("Keep overloaded methods/constructors together");
    overloadedMethodsBox.setSelected(settings.isKeepOverloadedMethodsTogether());
    final JLabel overloadOrderLabel = new JLabel("Ordering of constructors and overloaded methods:");
    final JComboBox overloadOrderBox = new JComboBox(
      new String[]{
        "Retain original order",
        "Order by number of parameters (ascending)",
        "Order by number of parameters (descending)"
      }
    );
    overloadOrderLabel.setEnabled(settings.isKeepOverloadedMethodsTogether());
    overloadOrderBox.setEnabled(settings.isKeepOverloadedMethodsTogether());
    overloadOrderBox.setSelectedIndex(settings.getOverloadedOrder());
    overloadedMethodsBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          settings.setKeepOverloadedMethodsTogether(overloadedMethodsBox.isSelected());
          overloadOrderLabel.setEnabled(settings.isKeepOverloadedMethodsTogether());
          overloadOrderBox.setEnabled(settings.isKeepOverloadedMethodsTogether());
        }
      }
    );
    final JCheckBox confirmBox = new JCheckBox("Confirm before rearranging");
    confirmBox.setSelected(settings.isAskBeforeRearranging());
    confirmBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          settings.setAskBeforeRearranging(confirmBox.isSelected());
        }
      }
    );

    final JCheckBox rearrangeInnerClasses = new JCheckBox("Rearrange contents of inner classes");
    rearrangeInnerClasses.setSelected(settings.isRearrangeInnerClasses());
    rearrangeInnerClasses.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        settings.setRearrangeInnerClasses(rearrangeInnerClasses.isSelected());
      }
    });
    gsPanel.add(getterSetterBox, constraints);
    constraints.gridy++;
    gsPanel.add(gettersSettersWithPropertyBox, constraints);
    constraints.gridy++;
//        constraints.insets = new Insets(0, 0, 0, 0);
    gsPanel.add(overloadedMethodsBox, constraints);
    constraints.gridy++;
    constraints.insets = new Insets(0, 30, 3, 0);
    gsPanel.add(overloadOrderLabel, constraints);
    constraints.gridy++;
    constraints.insets = new Insets(0, 30, 10, 0);
    constraints.fill = GridBagConstraints.NONE;
    gsPanel.add(overloadOrderBox, constraints);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridy++;
    constraints.insets = new Insets(5, 5, 0, 0);
    gsPanel.add(confirmBox, constraints);
    constraints.gridy++;
    gsPanel.add(rearrangeInnerClasses, constraints);
    constraints.gridy++;
    //
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0d;
    constraints.weighty = 0.0d;
    final JPanel gsDefaultPane = settings.getDefaultGSDefinition().getGSDefinitionPanel();
    constraints.insets = new Insets(5, 5, 0, 5);
    constraints.gridx = 1;
    int oldGridy = constraints.gridy;
    constraints.gridheight = constraints.gridy;
    constraints.gridy = 0;
    Border b = gsDefaultPane.getBorder();
    TitledBorder tb = BorderFactory.createTitledBorder(b, "Default Getter/Setter Definition");
    gsDefaultPane.setBorder(tb);
    gsPanel.add(gsDefaultPane, constraints);
    constraints.gridy = oldGridy;
    //
    constraints.gridx = 0;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.gridy++;
    constraints.weightx = 0; // was 1
    constraints.insets = new Insets(5, 5, 0, 0);
    final JLabel commentLabel = new JLabel("Comment pattern:");
    final JButton createPatternButton = new JButton("Generate pattern");
    final JButton verifyCommentsButton = new JButton("Verify comments against pattern");
    gsPanel.add(commentLabel, constraints);
    constraints.gridx++;
    constraints.anchor = GridBagConstraints.NORTH;
    gsPanel.add(createPatternButton, constraints);
    constraints.anchor = GridBagConstraints.NORTHEAST;
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1; // was 0
    constraints.insets = new Insets(5, 0, 0, 5);
    constraints.fill = GridBagConstraints.NONE;      // new line
    gsPanel.add(verifyCommentsButton, constraints);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridx = 0;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.insets = new Insets(5, 5, 5, 5);
    constraints.gridy++;
    constraints.weighty = 1.0d;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    final JTextArea commentArea = new JTextArea(4, 40);
    commentArea.setText(settings.getGlobalCommentPattern());
    constraints.gridheight = GridBagConstraints.REMAINDER;
    final JScrollPane scrollPane = new JScrollPane(commentArea);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    gsPanel.add(scrollPane, constraints);

    getterSetterBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          settings.setKeepGettersSettersTogether(getterSetterBox.isSelected());
          gettersSettersWithPropertyBox.setEnabled(settings.isKeepGettersSettersTogether());
        }
      }
    );

    gettersSettersWithPropertyBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        settings.setKeepGettersSettersWithProperty(gettersSettersWithPropertyBox.isSelected());
      }
    });

    overloadOrderBox.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          settings.setOverloadedOrder(overloadOrderBox.getSelectedIndex());
        }
      }
    );

    commentArea.getDocument().addDocumentListener(
      new DocumentListener() {
        public void changedUpdate(final DocumentEvent e) {
          settings.setGlobalCommentPattern(commentArea.getText());
        }

        public void insertUpdate(final DocumentEvent e) {
          settings.setGlobalCommentPattern(commentArea.getText());
        }

        public void removeUpdate(final DocumentEvent e) {
          settings.setGlobalCommentPattern(commentArea.getText());
        }
      }
    );

    createPatternButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String t = commentArea.getText();
          if (t.length() > 0) {
            int result = JOptionPane.showConfirmDialog(
              null, "This will replace the existing comment pattern with the generated pattern." +
                    "  Are you sure?",
              "Replace existing pattern",
              JOptionPane.YES_NO_CANCEL_OPTION
            );
            if (result != 0) return;
          }
          String s = generateCommentPattern(settings);
          commentArea.setText(s);
        }
      }
    );

    verifyCommentsButton.addActionListener(
      new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          checkCommentsAgainstGlobalPattern(settings);
        }
      }
    );
    return gsPanel;
  }

// ---------- START Level 5 ----------

  private String generateCommentPattern(RearrangerSettings settings) {
    new CommentUtil(settings);
    String result = RegexUtil.combineExpressions(CommentUtil.getCommentStrings());
    return result;
  }

  /**
   * Compare all comments specified by the user against the global comment pattern.  If all match, then
   * the global pattern will catch all the user's comments and remove them before rearranging and inserting
   * new ones.  If any don't match, bring up a dialog box listing them.
   *
   * @param settings current Rearranger settings.
   */
  public void checkCommentsAgainstGlobalPattern(RearrangerSettings settings) {
    String pattern = settings.getGlobalCommentPattern();
    if (pattern == null || pattern.trim().length() == 0) {
      JOptionPane.showMessageDialog(
        null, "No global comment pattern exists!"
        , "Verify Comments Against Pattern",
        JOptionPane.WARNING_MESSAGE
      );
      return;
    }
    try {
      " ".matches(pattern);
    }
    catch (PatternSyntaxException pse) {
      JOptionPane.showMessageDialog(
        null,
        "Invalid global pattern:" + pse.toString(),
        "Verify Comments Against Pattern",
        JOptionPane.ERROR_MESSAGE
      );
      return;
    }
    int nCommentsSeen = 0;
    List<String> mismatches = new ArrayList<String>();
    // first check all rules in the class order settings panes.
    nCommentsSeen =
      checkAttributeListForErrors(settings.getClassOrderAttributeList(), nCommentsSeen, pattern, mismatches);
    // now check all the class member attributes.
    nCommentsSeen =
      checkAttributeListForErrors(settings.getItemOrderAttributeList(), nCommentsSeen, pattern, mismatches);

    // now check all comments for extracted methods.  Expand keywords to make the match realistic.
    CommentRule rule = settings.getExtractedMethodsSettings().getPrecedingComment();
    String preceding = rule.getCommentText();
    if (preceding != null && preceding.trim().length() > 0) {
      nCommentsSeen++;
      checkExtractedComment(rule, "Preceding", pattern, mismatches);
    }
    rule = settings.getExtractedMethodsSettings().getTrailingComment();
    String trailing = rule.getCommentText();
    if (preceding != null && preceding.trim().length() > 0) {
      nCommentsSeen++;
      checkExtractedComment(rule, "Trailing", pattern, mismatches);
    }
    /**
     * if any strings mismatch, display a dialog showing them; otherwise display a dialog indicating that
     * everything is ok.
     */
    if (nCommentsSeen == 0) {
      JOptionPane.showMessageDialog(
        null, "No user-defined comments have been configured"
        , "Verify Comments Against Pattern",
        JOptionPane.PLAIN_MESSAGE
      );
    }
    else if (mismatches.size() == 0) {
      JOptionPane.showMessageDialog(
        null,
        nCommentsSeen +
        " user-defined comments seen; " +
        "all match the global comment pattern",
        "Verify Comments Against Pattern",
        JOptionPane.PLAIN_MESSAGE
      );
    }
    else {
      mismatches.add(0, nCommentsSeen + " user-defined comments seen; following comments did not match:");
      JOptionPane.showMessageDialog(
        null, mismatches.toArray(),
        "Verify Comments Against Pattern", JOptionPane.WARNING_MESSAGE
      );
    }
  }

// ---------- START Level 6 ----------

  private int checkAttributeListForErrors(List<AttributeGroup> list,
                                          int nCommentsSeen,
                                          String pattern,
                                          List<String> mismatches)
  {
    int index = 0;
    for (AttributeGroup group : list) {
      index++;
      nCommentsSeen += group.getCommentCount();
      if (!group.commentsMatchGlobalPattern(pattern)) {
        List badComments = group.getOffendingPatterns(pattern);
        for (Object badComment : badComments) {
          mismatches.add("Class Member item #" + index + ": \"" + badComment + "\"");
        }
      }
    }
    return nCommentsSeen;
  }

  private void checkExtractedComment(final CommentRule commentRule,
                                     final String which,
                                     String pattern,
                                     List<String> mismatches)
  {
    String comment = commentRule.getCommentText();
    if (comment.length() == 0) return;
    // make some 'typical' substitutions for wildcard expansion to check against pattern.
    // hand-expand the %FS% (fill string) because the fill string could contain special characters that
    // replaceAll interprets and changes.
    String expanded = comment.replaceAll("%LV%", "12");
    expanded = expanded.replaceAll("%TL%", "topLevelMethod()");
    expanded = expanded.replaceAll("%MN%", "methodName()");
    expanded = expanded.replaceAll("%AM%", "method1().[method1A(),method1B()]");
    expanded = expanded.replaceAll("%IF%", "Interface_ExampleName0");
    expanded = RegexUtil.replaceAllFS(expanded, commentRule.getCommentFillString().getFillString());
    if (!expanded.matches(pattern)) {
      mismatches.add(which + " comment for extracted methods: \"" + comment + "\"");
    }
  }

// ---------- END Level 6 ----------


// ---------- END Level 5 ----------

  private JPanel getConfigurationPane() {
    final JPanel gcPanel = new JPanel(new GridBagLayout());
    Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(5, 5, 0, 5);
    final JButton clearConfig = new JButton("Clear configuration");
    final JButton loadDefaultConfig = new JButton("Load default configuration");
    final JButton readConfig = new JButton("Read configuration from file...");
    final JButton writeConfig = new JButton("Write configuration to file...");
    clearConfig.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        createSettingsPanel(new RearrangerSettings());
      }
    });
    loadDefaultConfig.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        RearrangerSettings defaultSettings = RearrangerSettings.getDefaultSettings();
        if (defaultSettings == null) {
          JOptionPane.showMessageDialog(null, "Could not obtain default configuration.",
                                        "No default configuration available", JOptionPane.WARNING_MESSAGE);
        }
        else {
          createSettingsPanel(defaultSettings);
        }
      }
    });
    readConfig.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        int returnVal = fc.showOpenDialog(null);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          RearrangerSettings fileSettings = RearrangerSettings.getSettingsFromFile(file);
          if (fileSettings == null) {
            JOptionPane.showMessageDialog(null, "Could not read configuration.",
                                          "File or file contents invalid", JOptionPane.WARNING_MESSAGE);
          }
          else {
            createSettingsPanel(fileSettings);
          }
        }
      }
    });
    writeConfig.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser();
        int returnVal = fc.showSaveDialog(null);

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          settings.writeSettingsToFile(file);
        }
      }
    });
    gcPanel.add(clearConfig, constraints.weightedLastCol());
    constraints.newRow();
    gcPanel.add(loadDefaultConfig, constraints.weightedLastCol());
    constraints.newRow();
    gcPanel.add(readConfig, constraints.weightedLastCol());
    constraints.newRow();
    gcPanel.add(writeConfig, constraints.weightedLastCol());

    // now create outer panel so that buttons aren't stretched across creation.
    JPanel outerPanel = new JPanel(new GridBagLayout());
    constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.weightedLastRow();
    outerPanel.add(gcPanel, constraints.weightedLastCol());
    JPanel extraPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    extraPanel.setBorder(border);
    constraints = new Constraints(GridBagConstraints.SOUTHEAST);
    extraPanel.add(outerPanel, constraints.firstCol());
    constraints.weightedNewRow();
    constraints.firstCol(); // skip first column
    extraPanel.add(new JLabel(), constraints.weightedNextCol());
    constraints.lastRow();
    constraints.firstCol(); // skip first column
    constraints.nextCol(); // skip second column
    constraints.weightx = constraints.weighty = 0;
    constraints.insets = new Insets(5, 5, 5, 5);
    JLabel version = new JLabel("Rearranger plugin, version " + Rearranger.VERSION);
    extraPanel.add(version, constraints.lastCol());
    return extraPanel;
  }

// --------------------------- main() method ---------------------------

// ---------- END Level 4 ----------
// ---------- END Level 3 ----------


// ---------- END Level 2 ----------


// ---------- END Level 1 ----------
}
