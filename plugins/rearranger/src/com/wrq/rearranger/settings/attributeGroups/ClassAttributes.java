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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaToken;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.settings.CommentRule;
import com.wrq.rearranger.settings.atomicAttributes.AbstractAttribute;
import com.wrq.rearranger.settings.atomicAttributes.EnumAttribute;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/** Routines to handle class modifiers other than those already handled by CommonAttributes. */
public final class ClassAttributes
  extends CommonAttributes
  implements AttributeGroup
{

// ------------------------------ FIELDS ------------------------------

  private AbstractAttribute abAttr;
  private EnumAttribute     enumAttr;

// -------------------------- STATIC METHODS --------------------------

  public static AttributeGroup readExternal(final Element element) {
    if (element.getName().equals("Class")) {
      final ClassAttributes result = new ClassAttributes();
      CommonAttributes.readExternal(result, element);
      result.abAttr = AbstractAttribute.readExternal(element);
      result.enumAttr = EnumAttribute.readExternal(element);
      return result;
    }
    else if (element.getName().equals("Comment")) {
      return CommentRule.readExternal(element);
    }
    else {
      return null;
    }
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public ClassAttributes() {
    abAttr = new AbstractAttribute();
    enumAttr = new EnumAttribute();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  private AbstractAttribute getAbAttr() {
    return abAttr;
  }

  public EnumAttribute getEnumAttr() {
    return enumAttr;
  }
// ------------------------ CANONICAL METHODS ------------------------

  public final String toString() {
    final StringBuffer sb = new StringBuffer(70);
    sb.append(abAttr.getDescriptiveString());
    sb.append(plAttr.getProtectionLevelString());
    sb.append(stAttr.getDescriptiveString());
    sb.append(fAttr.getDescriptiveString());
    sb.append(enumAttr.getDescriptiveString());
    if (sb.length() == 0) {
      sb.append("all classes");
    }
    else {
      sb.append("classes");
    }
    if (nameAttr.isMatch()) {
      sb.append(' ');
      sb.append(nameAttr.getDescriptiveString());
    }
    sb.append(sortAttr.getDescriptiveString());
    return sb.toString();
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface AttributeGroup ---------------------

  public final /*ClassAttributes*/AttributeGroup deepCopy() {
    final ClassAttributes result = new ClassAttributes();
    deepCopyCommonItems(result);
    result.abAttr = (AbstractAttribute)abAttr.deepCopy();
    result.enumAttr = (EnumAttribute)enumAttr.deepCopy();
    return result;
  }

  public final void writeExternal(final Element parent) {
    final Element me = new Element("Class");
    writeExternalCommonAttributes(me);
    abAttr.appendAttributes(me);
    enumAttr.appendAttributes(me);
    parent.getChildren().add(me);
  }

// -------------------------- OTHER METHODS --------------------------

  public boolean equals(final Object obj) {
    if (!(obj instanceof ClassAttributes)) {
      return false;
    }
    final ClassAttributes ca = (ClassAttributes)obj;
    return super.equals(ca) &&
           abAttr.equals(ca.abAttr) &&
           enumAttr.equals(ca.enumAttr);
  }

  public final JPanel getClassAttributesPanel() {
    final JPanel caPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    caPanel.setBorder(border);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = 4;
    constraints.weightx = 1.0d;
    constraints.weighty = 0.0d;
    constraints.insets = new Insets(0, 0, 10, 0);
    caPanel.add(getPlAttr().getProtectionLevelPanel(), constraints);
    constraints.gridy = 4;
    constraints.gridheight = 1;
    caPanel.add(getStAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    caPanel.add(getAbAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    caPanel.add(getfAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    caPanel.add(getEnumAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    caPanel.add(getNameAttr().getStringPanel(), constraints);
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weighty = 1.0d;
    constraints.insets = new Insets(0, 0, 0, 0);
    caPanel.add(sortAttr.getSortOptionsPanel(), constraints);
    return caPanel;
  }

  public final boolean isMatch(RangeEntry entry)
  // final int modifiers, final String name, final String returnType)
  {
    return entry.getEnd().getParent() instanceof PsiClass &&
           entry.getEnd() instanceof PsiJavaToken &&
//               ((PsiJavaToken)entry.getEnd()).getTokenType() == PsiJavaToken.LBRACE &&
           entry.getEnd().getText().equals("{") &&
           super.isMatch(entry) &&
           abAttr.isMatch(entry.getModifiers()) &&
           enumAttr.isMatch(entry.getModifiers());
  }
}
