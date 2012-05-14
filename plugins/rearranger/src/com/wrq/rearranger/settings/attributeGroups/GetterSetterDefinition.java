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
package com.wrq.rearranger.settings.attributeGroups;

import com.wrq.rearranger.settings.RearrangerSettings;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/** Specifies criteria defining what constitutes a getter or setter method. */
public class GetterSetterDefinition {

// ------------------------------ FIELDS ------------------------------

  public static final int GETTER_BODY_RETURNS_FIELD = 0;
  public static final int GETTER_BODY_RETURNS       = 1;
  public static final int GETTER_BODY_IMMATERIAL    = 2;

  public static final int GETTER_NAME_MATCHES_FIELD  = 0;
  public static final int GETTER_NAME_CORRECT_PREFIX = 1;

  public static final int SETTER_BODY_SETS_FIELD = 0;
  public static final int SETTER_BODY_IMMATERIAL = 1;

  public static final int SETTER_NAME_MATCHES_FIELD  = 0;
  public static final int SETTER_NAME_CORRECT_PREFIX = 1;

  private int getterBodyCriterion;
  private int getterNameCriterion;
  private int setterBodyCriterion;
  private int setterNameCriterion;

// -------------------------- STATIC METHODS --------------------------

  public static GetterSetterDefinition readExternal(Element item) {
    final GetterSetterDefinition result = new GetterSetterDefinition();
    if (item != null) {
      Element child = item.getChild("GetterSetterDefinition");
      if (child != null) {
        result.loadAttributes(child);
      }
    }
    return result;
  }

  public void loadAttributes(Element me) {
    getterBodyCriterion = RearrangerSettings.getIntAttribute(me, "getterBody");
    getterNameCriterion = RearrangerSettings.getIntAttribute(me, "getterName");
    setterBodyCriterion = RearrangerSettings.getIntAttribute(me, "setterBody");
    setterNameCriterion = RearrangerSettings.getIntAttribute(me, "setterName");
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public int getGetterBodyCriterion() {
    return getterBodyCriterion;
  }

  public void setGetterBodyCriterion(int getterBodyCriterion) {
    this.getterBodyCriterion = getterBodyCriterion;
  }

  public int getGetterNameCriterion() {
    return getterNameCriterion;
  }

  public void setGetterNameCriterion(int getterNameCriterion) {
    this.getterNameCriterion = getterNameCriterion;
  }

  public int getSetterBodyCriterion() {
    return setterBodyCriterion;
  }

  public void setSetterBodyCriterion(int setterBodyCriterion) {
    this.setterBodyCriterion = setterBodyCriterion;
  }

  public int getSetterNameCriterion() {
    return setterNameCriterion;
  }

  public void setSetterNameCriterion(int setterNameCriterion) {
    this.setterNameCriterion = setterNameCriterion;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(Object obj) {
    if (obj instanceof GetterSetterDefinition) {
      GetterSetterDefinition gsd = (GetterSetterDefinition)obj;
      if (gsd.getterBodyCriterion == getterBodyCriterion &&
          gsd.getterNameCriterion == getterNameCriterion &&
          gsd.setterBodyCriterion == setterBodyCriterion &&
          gsd.setterNameCriterion == setterNameCriterion)
      {
        return true;
      }
    }
    return false;
  }

// -------------------------- OTHER METHODS --------------------------

  public final GetterSetterDefinition deepCopy() {
    final GetterSetterDefinition result = new GetterSetterDefinition();
    result.getterBodyCriterion = getterBodyCriterion;
    result.getterNameCriterion = getterNameCriterion;
    result.setterBodyCriterion = setterBodyCriterion;
    result.setterNameCriterion = setterNameCriterion;
    return result;
  }

  public JPanel getGSDefinitionPanel() {
    final JPanel gsdPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = 1.0d;
    constraints.weighty = 0.0d;
    constraints.gridx = constraints.gridy = 0;
    constraints.insets = new Insets(5, 5, 0, 5);
    {
      JPanel getterPanel = new JPanel(new GridBagLayout());
      final Border getterBorder = BorderFactory.createEtchedBorder();
      final TitledBorder titledBorder = BorderFactory.createTitledBorder(getterBorder, "Getter definition");
      getterPanel.setBorder(titledBorder);
      final GridBagConstraints gc = new GridBagConstraints();
      gc.gridwidth = 1;
      gc.gridheight = 1;
      gc.anchor = GridBagConstraints.WEST;
      gc.fill = GridBagConstraints.BOTH;
      gc.weightx = 0;
      gc.weighty = 0;
      gc.gridx = gc.gridy = 0;
      JLabel methodBodyLabel = new JLabel("Method body:");
      getterPanel.add(methodBodyLabel, gc);
      gc.gridx++;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1.0;
      final JComboBox getterBodyBox = new JComboBox(
        new Object[]{
          "Contains \"return <field>;\" only",
          "Contains one return statement only",
          "Ignore body"
        }
      );
      JLabel methodNameLabel = new JLabel("Method name:");
      final JComboBox getterNameBox = new JComboBox(
        new Object[]{
          "Has prefix plus correct field name",
          "Has \"get/is/has\" prefix",
        }
      );
      getterPanel.add(getterBodyBox, gc);
      gc.gridy++;
      getterBodyBox.setSelectedIndex(getterBodyCriterion);
      getterBodyBox.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            getterBodyCriterion = getterBodyBox.getSelectedIndex();
            if (getterBodyCriterion == GETTER_BODY_RETURNS_FIELD &&
                getterNameCriterion != GETTER_NAME_MATCHES_FIELD)
            {
              getterNameBox.setSelectedIndex(getterNameCriterion = GETTER_NAME_MATCHES_FIELD);
            }
            if (getterBodyCriterion != GETTER_BODY_RETURNS_FIELD &&
                getterNameCriterion != GETTER_NAME_CORRECT_PREFIX)
            {
              getterNameBox.setSelectedIndex(getterNameCriterion = GETTER_NAME_CORRECT_PREFIX);
            }
          }
        }
      );
      gc.weightx = 0;
      gc.gridx = 0;
      gc.gridwidth = 1;
      getterPanel.add(methodNameLabel, gc);
      gc.gridx++;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1.0;
      getterNameBox.setSelectedIndex(getterNameCriterion);
      getterPanel.add(getterNameBox, gc);
      gc.gridy++;
      getterNameBox.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            getterNameCriterion = getterNameBox.getSelectedIndex();
            if (getterNameCriterion == GETTER_NAME_MATCHES_FIELD &&
                getterBodyCriterion != GETTER_BODY_RETURNS_FIELD)
            {
              getterBodyBox.setSelectedIndex(getterBodyCriterion = GETTER_BODY_RETURNS_FIELD);
            }
            if (getterNameCriterion == GETTER_NAME_CORRECT_PREFIX &&
                getterBodyCriterion == GETTER_BODY_RETURNS_FIELD)
            {
              getterBodyBox.setSelectedIndex(getterBodyCriterion = GETTER_BODY_RETURNS);
            }
          }
        }
      );
      gsdPanel.add(getterPanel, constraints);
    }
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weighty = 1;
    {
      JPanel setterPanel = new JPanel(new GridBagLayout());
      final Border setterBorder = BorderFactory.createEtchedBorder();
      final TitledBorder titledBorder = BorderFactory.createTitledBorder(setterBorder, "Setter definition");
      setterPanel.setBorder(titledBorder);
      final GridBagConstraints gc = new GridBagConstraints();
      gc.gridwidth = 1;
      gc.gridheight = 1;
      gc.anchor = GridBagConstraints.WEST;
      gc.fill = GridBagConstraints.BOTH;
      gc.weightx = 0;
      gc.weighty = 0;
      gc.gridx = gc.gridy = 0;
      JLabel methodBodyLabel = new JLabel("Method body:");
      setterPanel.add(methodBodyLabel, gc);
      gc.gridx++;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1.0;
      final JComboBox setterBodyBox = new JComboBox(
        new Object[]{
          "Contains one assignment statement \"<field> = <param>;\" only",
          "Ignore body"}
      );
      final JComboBox setterNameBox = new JComboBox(
        new Object[]{
          "Has prefix plus correct field name",
          "Has \"set\" prefix",
        }
      );
      setterPanel.add(setterBodyBox, gc);
      gc.gridy++;
      setterBodyBox.setSelectedIndex(setterBodyCriterion);
      setterBodyBox.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            setterBodyCriterion = setterBodyBox.getSelectedIndex();
            if (setterBodyCriterion == SETTER_BODY_IMMATERIAL &&
                setterNameCriterion != SETTER_NAME_CORRECT_PREFIX)
            {
              setterNameBox.setSelectedIndex(setterNameCriterion = SETTER_NAME_CORRECT_PREFIX);
            }
            if (setterBodyCriterion == SETTER_BODY_SETS_FIELD &&
                setterNameCriterion != SETTER_NAME_MATCHES_FIELD)
            {
              setterNameBox.setSelectedIndex(setterNameCriterion = SETTER_NAME_MATCHES_FIELD);
            }
          }
        }
      );
      gc.weightx = 0;
      gc.gridx = 0;
      gc.gridwidth = 1;
      JLabel methodNameLabel = new JLabel("Method name:");
      setterPanel.add(methodNameLabel, gc);
      gc.gridx++;
      gc.gridwidth = GridBagConstraints.REMAINDER;
      gc.weightx = 1.0;
      setterPanel.add(setterNameBox, gc);
      setterNameBox.setSelectedIndex(setterNameCriterion);
      gsdPanel.add(setterPanel, constraints);
      setterNameBox.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            setterNameCriterion = setterNameBox.getSelectedIndex();
            if (setterNameCriterion == SETTER_NAME_CORRECT_PREFIX &&
                setterBodyCriterion != SETTER_BODY_IMMATERIAL)
            {
              setterBodyBox.setSelectedIndex(setterBodyCriterion = SETTER_BODY_IMMATERIAL);
            }
            if (setterNameCriterion == SETTER_NAME_MATCHES_FIELD &&
                setterBodyCriterion != SETTER_BODY_SETS_FIELD)
            {
              setterBodyBox.setSelectedIndex(setterBodyCriterion = SETTER_BODY_SETS_FIELD);
            }
          }
        }
      );
    }
    return gsdPanel;
  }

  public final void writeExternal(final Element items) {
    appendAttributes(items);
  }

  public final void appendAttributes(final Element me) {
    final Element stElement = new Element("GetterSetterDefinition");
    me.getChildren().add(stElement);
    stElement.setAttribute("getterBody", "" + getterBodyCriterion);
    stElement.setAttribute("getterName", "" + getterNameCriterion);
    stElement.setAttribute("setterBody", "" + setterBodyCriterion);
    stElement.setAttribute("setterName", "" + setterNameCriterion);
  }
}