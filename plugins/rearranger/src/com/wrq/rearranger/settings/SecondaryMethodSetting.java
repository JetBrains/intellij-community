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

import com.wrq.rearranger.configuration.IListManagerObject;
import com.wrq.rearranger.configuration.IntTextField;
import com.wrq.rearranger.configuration.StringTextArea;
import com.wrq.rearranger.configuration.StringTextField;

import javax.swing.*;
import java.awt.*;


/**
 * Contains settings for each secondary method (e.g., "set", "is").  Any number of secondary method definitions
 * may be associated with a primary method definition.
 */
public class SecondaryMethodSetting
  implements IListManagerObject
{
  /** Description of the related method. */
  String description;
  /**
   * Regular expression which method name must match.  If the method is to be matched to a field, there must be
   * at least one capture group (parenthesized subexpression) which will be treated as the field.
   * In the case of a traditional setter method, this pattern is "set%FN%".   One substitution is available:
   * <pre>
   * %FN%  field name
   * </pre>
   */
  String methodNamePattern;
  /** number of parameters the secondary method must have.  In the case of a traditional setter method, this is one. */
  int    numParameters;
  /**
   * regular expression pattern of the type which the secondary method must return.
   * In the case of a traditional getter method, this is the field type.  One substitution is available:
   * <pre>
   * %FT%  field type
   * </pre>
   */
  String methodTypePattern;
  /**
   * regular expression pattern of the attributes of the method.
   * In the case of a traditional getter method, this is ".*public.*" (i.e., any public method).
   */
  String methodAttributePattern;
  /**
   * pattern of method body (excluding comments and newline characters).  Substitutions are available as follows:
   * <pre>
   * %FN%  field name
   * %FT%  field type (if field is valid field, else empty string)
   * %MT%  method return type
   * %P1%  parameter 1 name (%P2%, %P3% etc for additional parameters)
   * %T1%  parameter 1 type name
   * </pre>
   */
  String body;

  public SecondaryMethodSetting() {
    methodNamePattern = "set%FN%";
    numParameters = 1;
    methodTypePattern = "void";
    methodAttributePattern = ".*public.*";
    body = "{\\s*return\\s*%FN%;\\s*}";
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getMethodNamePattern() {
    return methodNamePattern;
  }

  public void setMethodNamePattern(String methodNamePattern) {
    this.methodNamePattern = methodNamePattern;
  }

  public int getNumParameters() {
    return numParameters;
  }

  public void setNumParameters(int numParameters) {
    this.numParameters = numParameters;
  }

  public String getMethodTypePattern() {
    return methodTypePattern;
  }

  public void setMethodTypePattern(String methodTypePattern) {
    this.methodTypePattern = methodTypePattern;
  }

  public String getMethodAttributePattern() {
    return methodAttributePattern;
  }

  public void setMethodAttributePattern(String methodAttributePattern) {
    this.methodAttributePattern = methodAttributePattern;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String toString() {
    return description;
  }

  public com.wrq.rearranger.configuration.IListManagerObject deepcopy() {
    SecondaryMethodSetting pms = new SecondaryMethodSetting();
    pms.description = getDescription();
    pms.methodNamePattern = getMethodNamePattern();
    pms.numParameters = getNumParameters();
    pms.methodTypePattern = getMethodTypePattern();
    pms.methodAttributePattern = getMethodAttributePattern();
    pms.body = getBody();
    return pms;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof PrimaryMethodSetting)) return false;
    PrimaryMethodSetting pms = (PrimaryMethodSetting)obj;
    if (!pms.description.equals(description)) return false;
    if (!pms.methodNamePattern.equals(methodNamePattern)) return false;
    if (pms.numParameters != numParameters) return false;
    if (!pms.methodTypePattern.equals(methodTypePattern)) return false;
    if (!pms.methodAttributePattern.equals(methodAttributePattern)) return false;
    if (!pms.body.equals(body)) return false;
    return true;
  }

  public JPanel getPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
                                                     "Matching Method (\"Setter\") Criteria"));
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 0.0d;
    constraints.weighty = 0.0d;
    constraints.gridy = constraints.gridx = 0;
    constraints.insets = new Insets(3, 3, 3, 3);
    JLabel methodNamePatternLabel = new JLabel("Method name:");
    methodNamePatternLabel.setToolTipText(
      "Regular expression which method name must match.  " +
      "If the method is to be matched to a field, there must be\n" +
      " at least one capture group (parenthesized subexpression) which will be treated as the field.\n" +
      " In the case of a traditional setter method, this pattern is \"set%FN%\".\n" +
      " One substitution is available:\n" +
      "    %FN%  field name");
    panel.add(methodNamePatternLabel, constraints);
    constraints.gridx++;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.weightx = .5;
    StringTextField primaryPrefixText = new StringTextField() {
      public void setValue(String value) {
        setMethodNamePattern(value);
      }

      public String getValue() {
        return getMethodNamePattern();
      }
    };
    panel.add(primaryPrefixText, constraints);
    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 0;
    constraints.gridx++;
    JLabel nParamLabel = new JLabel("Number of parameters:");
    nParamLabel.setToolTipText(
      "number of parameters the secondary method must have.\n" +
      "In the case of a traditional setter method, this is 1.");
    panel.add(nParamLabel, constraints);
    constraints.gridx++;
    IntTextField nParamText = new IntTextField(
      new IntTextField.IGetSet() {
        public int get() {
          return numParameters;
        }

        public void set(int value) {
          numParameters = value;
        }
      }, null, null
    );
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(nParamText, constraints);

    constraints.gridx = 0;
    constraints.gridy++;
    constraints.gridwidth = 1;
    constraints.weightx = 0;
    constraints.fill = GridBagConstraints.NONE;
    JLabel returnTypeLabel = new JLabel("Return type:");
    returnTypeLabel.setToolTipText(
      "regular expression pattern of the type which the secondary method must return.\n" +
      " In the case of a traditional setter method, this is void.  One substitution is available:\n" +
      "     * %FT%  field type");
    panel.add(returnTypeLabel, constraints);
    constraints.gridx++;
    constraints.weightx = .5;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    StringTextField returnTypeField = new StringTextField() {
      public void setValue(String value) {
        methodTypePattern = value;
      }

      public String getValue() {
        return methodTypePattern;
      }
    };
    panel.add(returnTypeField, constraints);
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridx++;
    constraints.weightx = 0;
    JLabel methodAttributeLabel = new JLabel("Method attributes:");
    methodAttributeLabel.setToolTipText("Protection level or other attributes for the method.\n" +
                                        "In the case of a getter method, this is usually \".*public.*\".");
    panel.add(methodAttributeLabel, constraints);
    StringTextField methodAttributeField = new StringTextField() {
      public void setValue(String value) {
        methodAttributePattern = value;
      }

      public String getValue() {
        return methodAttributePattern;
      }
    };
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = .5;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    panel.add(methodAttributeField, constraints);
    constraints.gridx++;
    constraints.weightx = 0;
    constraints.fill = GridBagConstraints.NONE;
    constraints.gridy++;
    constraints.gridx = 0;
    JLabel bodyLabel = new JLabel("Body:");
    bodyLabel.setToolTipText("regular expression pattern of method body (excluding comments and newline characters).\n" +
                             "Substitutions are available as follows:\n" +
                             " %FN%  field name\n" +
                             " %FT%  field type (if field is valid field, else empty string)\n" +
                             " %MT%  method return type\n" +
                             " %P1%  parameter 1 name (%P2%, %P3% etc for additional parameters)\n" +
                             " %T1%  parameter 1 type name");
    constraints.gridwidth = 1;
    constraints.weightx = 0;
    constraints.fill = GridBagConstraints.NONE;
    panel.add(bodyLabel, constraints);
    constraints.gridx++;
    StringTextArea bodyField = new StringTextArea() {
      public void setValue(String value) {
        body = value;
      }

      public String getValue() {
        return body;
      }
    };
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    constraints.weighty = .35;
    constraints.fill = GridBagConstraints.BOTH;
    JScrollPane sp = new JScrollPane(bodyField);
    panel.add(sp, constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.fill = GridBagConstraints.NONE;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1;
    constraints.weighty = .65;
    return panel;
  }
}
