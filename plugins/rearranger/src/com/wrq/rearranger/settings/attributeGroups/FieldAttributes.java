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

import com.intellij.psi.PsiField;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.settings.atomicAttributes.InitToAnonClassAttribute;
import com.wrq.rearranger.settings.atomicAttributes.TransientAttribute;
import com.wrq.rearranger.settings.atomicAttributes.TypeAttribute;
import com.wrq.rearranger.settings.atomicAttributes.VolatileAttribute;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/** Routines to handle field attributes not covered by CommonAttributes. */
public final class FieldAttributes
  extends ItemAttributes
{

// ------------------------------ FIELDS ------------------------------

  private InitToAnonClassAttribute initToAnonClassAttr;
  private TransientAttribute       transientAttr;
  private VolatileAttribute        volatileAttr;
  private TypeAttribute            typeAttr;

// -------------------------- STATIC METHODS --------------------------

  public static /*FieldAttributes*/AttributeGroup readExternal(final Element item) {
    final FieldAttributes result = new FieldAttributes();
    CommonAttributes.readExternal(result, item);
    result.initToAnonClassAttr = InitToAnonClassAttribute.readExternal(item);
    result.transientAttr = TransientAttribute.readExternal(item);
    result.volatileAttr = VolatileAttribute.readExternal(item);
    result.typeAttr = TypeAttribute.readExternal(item);
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public FieldAttributes() {
    initToAnonClassAttr = new InitToAnonClassAttribute();
    transientAttr = new TransientAttribute();
    volatileAttr = new VolatileAttribute();
    typeAttr = new TypeAttribute();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public InitToAnonClassAttribute getInitToAnonClassAttr() {
    return initToAnonClassAttr;
  }

  private TransientAttribute getTransientAttr() {
    return transientAttr;
  }

  public TypeAttribute getTypeAttr() {
    return typeAttr;
  }

  private VolatileAttribute getVolatileAttr() {
    return volatileAttr;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public final String toString() {
    final StringBuilder sb = new StringBuilder(70);
    sb.append(getProtectionLevelAttributes().getProtectionLevelString());
    sb.append(getStaticAttribute().getDescriptiveString());
    sb.append(getFinalAttribute().getDescriptiveString());
    sb.append(transientAttr.getDescriptiveString());
    sb.append(volatileAttr.getDescriptiveString());

    if (sb.length() == 0) {
      sb.append("all fields");
    }
    else {
      sb.append("fields");
    }
    if (initToAnonClassAttr.isValue()) {
      if (!initToAnonClassAttr.isInvert()) {
        sb.append(" which are initialized to an anonymous class");
      }
      else {
        sb.append(" which are not initialized to an anonymous class");
      }
    }
    if (getNameAttribute().isMatch()) {
      if (initToAnonClassAttr.isValue()) {
        sb.append(" and");
      }
      sb.append(' ');
      sb.append(getNameAttribute().getDescriptiveString());
    }
    if (typeAttr.isMatch()) {
      if (getNameAttribute().isMatch() || initToAnonClassAttr.isValue()) {
        sb.append(" and");
      }
      sb.append(' ');
      sb.append(typeAttr.getDescriptiveString());
    }
    sb.append(getSortOptions().getDescriptiveString());
    return sb.toString();
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface AttributeGroup ---------------------

  @NotNull
  public final /*FieldAttributes*/AttributeGroup deepCopy() {
    final FieldAttributes result = new FieldAttributes();
    deepCopyCommonItems(result);
    result.initToAnonClassAttr = (InitToAnonClassAttribute)initToAnonClassAttr.deepCopy();
    result.transientAttr = (TransientAttribute)transientAttr.deepCopy();
    result.volatileAttr = (VolatileAttribute)volatileAttr.deepCopy();
    result.typeAttr = (TypeAttribute)typeAttr.deepCopy();
    return result;
  }

  @SuppressWarnings("unchecked")
  public final void writeExternal(@NotNull final Element parent) {
    final Element child = new Element("Field");
    writeExternalCommonAttributes(child);
    initToAnonClassAttr.appendAttributes(child);
    transientAttr.appendAttributes(child);
    volatileAttr.appendAttributes(child);
    typeAttr.appendAttributes(child);
    parent.getChildren().add(child);
  }

// -------------------------- OTHER METHODS --------------------------

  public boolean equals(final Object object) {
    if (!(object instanceof FieldAttributes)) return false;
    final FieldAttributes fa = (FieldAttributes)object;
    return super.equals(fa) &&
           transientAttr.equals(fa.transientAttr) &&
           volatileAttr.equals(fa.volatileAttr) &&
           initToAnonClassAttr.equals(fa.initToAnonClassAttr) &&
           typeAttr.equals(fa.typeAttr);
  }

  public JPanel getFieldAttributes() {
    final JPanel plPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    plPanel.setBorder(border);
    final Constraints constraints = new Constraints(GridBagConstraints.NORTHWEST);
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridwidth = 1;
    constraints.gridheight = 4;
    constraints.weightx = 1.0d;
    plPanel.add(getProtectionLevelAttributes().getProtectionLevelPanel(), constraints);
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridy = 0;
    constraints.gridx = 1;
    constraints.gridheight = 1;
    constraints.weighty = 0;
    plPanel.add(getStaticAttribute().getAndNotPanel(), constraints);
    constraints.gridy++;
    plPanel.add(getFinalAttribute().getAndNotPanel(), constraints);
    constraints.gridy++;
    plPanel.add(getTransientAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    plPanel.add(getVolatileAttr().getAndNotPanel(), constraints);
    constraints.insets = new Insets(5, 0, 0, 0);
    constraints.gridx = 0;
    constraints.gridy++;
    plPanel.add(getInitToAnonClassAttr().getAndNotPanel(), constraints);
    constraints.gridy++;
    plPanel.add(getNameAttribute().getStringPanel(), constraints);
    constraints.gridy++;
    plPanel.add(getTypeAttr().getStringPanel(), constraints);
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weighty = 1.0d;
    constraints.insets = new Insets(0, 0, 0, 0);
    plPanel.add(getSortOptions().getSortOptionsPanel(), constraints);
    return plPanel;
  }

  public boolean isMatch(@NotNull RangeEntry rangeEntry) {
    return rangeEntry.getEnd() instanceof PsiField &&
           initToAnonClassAttr.isMatch(rangeEntry.getModifiers()) &&
           transientAttr.isMatch(rangeEntry.getModifiers()) &&
           volatileAttr.isMatch(rangeEntry.getModifiers()) &&
           typeAttr.isMatch(rangeEntry.getType()) &&
           super.isMatch(rangeEntry);
  }
}
