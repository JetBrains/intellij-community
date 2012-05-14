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

import com.intellij.ui.DocumentAdapter;
import com.wrq.rearranger.util.Constraints;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.text.NumberFormat;

/**
 * Getters and setters are defined by their method names (eg., beginning with "get"), number of parameters,
 * return type, and body contents.  This class provides support for these common rule attributes.
 * Other attributes, like number of parameters or return type, are already specified in MethodAttributes.
 */
public class MethodSpec {
  /**
   * pattern for property name.  Must contain one or more regular expression groups, one of which will be
   * regarded as the property.  Will be matched against the candidate method's name.
   */
  String  propertyPattern;
  /** Group number which identifies which regex group contains the pattern. */
  int     propertyGroup;
  /** true if first letter of property should be lowercased. */
  boolean lowercaseFirstLetterOfProperty;
  /** if true, the property name must match a field in the class, else the method will not match. */
  boolean propertyMustMatchField;
  /**
   * pattern of method body (excluding comments and newline characters).  Substitutions will be performed
   * in this pattern before matching against the method's body as follows:
   * <pre>
   * %PATTERN%        this method's pattern
   * %TYPE%           this method's type
   * %PARAMNAME<n>%   name of parameter <n>, <n> = 1, 2, 3, ...
   * $PARAMTYPE<n>%   type of parameter <n>, <n> = 1, 2, 3, ...
   * $PARENT%         parent's property
   * </pre>
   */
  String  body;
  /** true if body matching should be done against method body stripped of comments. */
  boolean ignoreComments;
  /**
   * value of "parent property", which is the property of the field or method executing this rule.
   * If this is the top level rule (not being called by another rule to associate methods matching this
   * rule with the parent), then there is no parent property.
   */
  final String parentProperty;

  public MethodSpec(String parentProperty) {
    this.parentProperty = parentProperty;
  }

  public String getPropertyPattern() {
    return propertyPattern;
  }

  public void setPropertyPattern(String propertyPattern) {
    this.propertyPattern = propertyPattern;
  }

  public int getPropertyGroup() {
    return propertyGroup;
  }

  public void setPropertyGroup(int propertyGroup) {
    this.propertyGroup = propertyGroup;
  }

  public boolean isLowercaseFirstLetterOfProperty() {
    return lowercaseFirstLetterOfProperty;
  }

  public void setLowercaseFirstLetterOfProperty(boolean lowercaseFirstLetterOfProperty) {
    this.lowercaseFirstLetterOfProperty = lowercaseFirstLetterOfProperty;
  }

  public boolean isPropertyMustMatchField() {
    return propertyMustMatchField;
  }

  public void setPropertyMustMatchField(boolean propertyMustMatchField) {
    this.propertyMustMatchField = propertyMustMatchField;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public boolean isIgnoreComments() {
    return ignoreComments;
  }

  public void setIgnoreComments(boolean ignoreComments) {
    this.ignoreComments = ignoreComments;
  }

  boolean isMatch() {
    return false; // todo
  }

  private JFormattedTextField getIntField(int initialValue) {
    final NumberFormat integerInstance = NumberFormat.getIntegerInstance();
    integerInstance.setMaximumIntegerDigits(2);
    integerInstance.setMinimumIntegerDigits(1);
    final JFormattedTextField intField = new JFormattedTextField(integerInstance);
    intField.setValue(new Integer("88"));
    final Dimension d = intField.getPreferredSize();
    d.width += 3;
    intField.setPreferredSize(d);
    intField.setValue(initialValue);
    intField.setFocusLostBehavior(JFormattedTextField.COMMIT_OR_REVERT);
    return intField;
  }

  public JPanel getPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    Constraints constraints = new Constraints();
    constraints.insets = new Insets(3, 3, 3, 3);
    JLabel propertyPatternLabel = new JLabel("Property pattern:");
    propertyPatternLabel.setToolTipText("Regex pattern which method name must match; " +
                                        "parenthesize portion which will be regarded as the method's property" +
                                        ", e.g. \"(get|is|has)([A-Z]\\w*)\"");
    panel.add(propertyPatternLabel, constraints.firstCol());
    constraints.fill = GridBagConstraints.HORIZONTAL;
    final JTextField propertyPatternField = new JTextField(this.propertyPattern);
    propertyPatternField.getDocument().addDocumentListener(
      new DocumentAdapter() {
        protected void textChanged(DocumentEvent event) {
          propertyPattern = propertyPatternField.getText();
        }
      }
    );
    panel.add(propertyPatternField, constraints.weightedLastCol(0.5));
    constraints.newRow();
    constraints.fill = GridBagConstraints.NONE;
    final JLabel propertyGroupLabel = new JLabel("Property group number:");
    propertyGroupLabel.setToolTipText("Regular expression parenthesized group corresponding to the property name");
    panel.add(propertyGroupLabel, constraints.firstCol());
    final JFormattedTextField propertyGroupField = getIntField(propertyGroup);
    panel.add(propertyGroupField, constraints.nextCol());
    constraints.newRow();
    JCheckBox lowerCase = new JCheckBox("Lowercase first letter of property");
    lowerCase.setToolTipText("If selected, lower cases the first letter of the property found in the regular expression");
    panel.add(lowerCase, constraints.lastCol());
    constraints.newRow();
    JCheckBox mustMatch = new JCheckBox("Property must match field name");
    mustMatch.setToolTipText("If selected, the property name derived from the regular expression must match the " +
                             "name of a field in the same class as the method; if no such field exists, the " +
                             "method will not match the rule");
    panel.add(mustMatch, constraints.lastCol());
//        propertyGroupField.get
    constraints.newRow();
    JLabel bodyLabel = new JLabel("Body:");
    bodyLabel.setToolTipText("Regex pattern for method body (excluding comments);\n" +
                             "use \"%FN%\" for field name, \"%P1%\" for param 1, etc.");
    constraints.fill = GridBagConstraints.NONE;
    panel.add(bodyLabel, constraints.firstCol());
    JCheckBox stripComments = new JCheckBox("Strip comments");
    stripComments.setToolTipText("If selected, comments will be stripped from the method body before attempting" +
                                 " to match the supplied expression");
    panel.add(stripComments, constraints.lastCol());
    constraints.weightedLastRow();
    constraints.fill = GridBagConstraints.BOTH;
    JTextArea body = new JTextArea(4, 40);
    JScrollPane sp = new JScrollPane(body);
    panel.add(sp, constraints.weightedLastCol());

    return panel;
  }

  public MethodSpec deepcopy() {
    MethodSpec result = new MethodSpec(parentProperty);
    result.body = body;
    result.ignoreComments = ignoreComments;
    result.lowercaseFirstLetterOfProperty = lowercaseFirstLetterOfProperty;
    result.propertyGroup = propertyGroup;
    result.propertyMustMatchField = propertyMustMatchField;
    result.propertyPattern = propertyPattern;
    result.body = body;
    return result;
  }

  public static void main(String[] args) {
    MethodSpec ms = new MethodSpec(null);
    final JDialog frame = new JDialog((Frame)null, "SwingApplication");
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0d;
    constraints.weighty = 1.0d;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridx = 0;
    constraints.gridy = 0;
    frame.getContentPane().setLayout(new GridBagLayout());
    frame.getContentPane().add(ms.getPanel(), constraints);
    //Finish setting up the frame, and show it.
    frame.pack();
    frame.setResizable(true);
    frame.setModal(true);
    frame.setVisible(true);
  }
}