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

import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Contains sorting attributes (part of common attributes; applies to fields, methods, inner and outer classes).
 * <p/>
 * Originally, methods and classes could only be sorted alphabetically by name (case sensitive).  Fields could be
 * sorted by type (optionally ignoring case), by name (case sensitive), or both.
 * <p/>
 * Beginning with release 4.4, all of these types (fields, methods and classes) can be sorted optionally by
 * modifiers, optionally by type (optionally ignoring case), and by name (optionally ignoring case).  This covers
 * all permutations.  Modifiers are always lower case so no need to ask about whether to ignore case.
 */
public class SortOptions
  extends AtomicAttribute
{
  private boolean byModifiers;
  private boolean byType;
  private boolean isTypeCaseInsensitive;
  private boolean byName;
  private boolean isNameCaseInsensitive;

  public SortOptions() {
    byModifiers = false;
    byType = false;
    isTypeCaseInsensitive = false;
    byName = false;
    isNameCaseInsensitive = false;
  }

  public boolean isByModifiers() {
    return byModifiers;
  }

  public void setByModifiers(boolean byModifiers) {
    this.byModifiers = byModifiers;
  }

  public boolean isByName() {
    return byName;
  }

  public void setByName(boolean byName) {
    this.byName = byName;
  }

  public boolean isByType() {
    return byType;
  }

  public void setByType(boolean byType) {
    this.byType = byType;
  }

  public boolean isNameCaseInsensitive() {
    return isNameCaseInsensitive;
  }

  public void setNameCaseInsensitive(boolean nameCaseInsensitive) {
    isNameCaseInsensitive = nameCaseInsensitive;
  }

  public boolean isTypeCaseInsensitive() {
    return isTypeCaseInsensitive;
  }

  public void setTypeCaseInsensitive(boolean typeCaseInsensitive) {
    isTypeCaseInsensitive = typeCaseInsensitive;
  }

  public final boolean equals(final Object obj) {
    if (!(obj instanceof SortOptions)) return false;
    SortOptions so = (SortOptions)obj;
    return byModifiers == so.byModifiers &&
           byType == so.byType &&
           isTypeCaseInsensitive == so.isTypeCaseInsensitive &&
           byName == so.byName &&
           isNameCaseInsensitive == so.isNameCaseInsensitive;
  }

  public static SortOptions readExternal(final Element item) {
    SortOptions result = new SortOptions();
    final Element me = item.getChild("SortOptions");
    result.loadAttributes(me);
    // support old settings
    result.byName |= RearrangerSettings.getBooleanAttribute(item, "alphabetize");
    result.byType |= RearrangerSettings.getBooleanAttribute(item, "sortByType", false);
    result.isTypeCaseInsensitive |= RearrangerSettings.getBooleanAttribute(item, "ignoreTypeCase", false);
    return result;
  }

  private void loadAttributes(final Element me) {
    byModifiers = RearrangerSettings.getBooleanAttribute(me, "byModifiers");
    byType = RearrangerSettings.getBooleanAttribute(me, "byType");
    isTypeCaseInsensitive = RearrangerSettings.getBooleanAttribute(me, "isTypeCaseInsensitive");
    byName = RearrangerSettings.getBooleanAttribute(me, "byName");
    isNameCaseInsensitive = RearrangerSettings.getBooleanAttribute(me, "isNameCaseInsensitive");
  }

  public final /*SortOptions*/ AtomicAttribute deepCopy() {
    final SortOptions result = new SortOptions();
    result.byModifiers = byModifiers;
    result.byType = byType;
    result.isTypeCaseInsensitive = isTypeCaseInsensitive;
    result.byName = byName;
    result.isNameCaseInsensitive = isNameCaseInsensitive;
    return result;
  }

  public final void appendAttributes(final Element me) {
    final Element plElement = new Element("SortOptions");
    me.getChildren().add(plElement);
    plElement.setAttribute("byModifiers", Boolean.valueOf(byModifiers).toString());
    plElement.setAttribute("byType", Boolean.valueOf(byType).toString());
    plElement.setAttribute("isTypeCaseInsensitive", Boolean.valueOf(isTypeCaseInsensitive).toString());
    plElement.setAttribute("byName", Boolean.valueOf(byName).toString());
    plElement.setAttribute("isNameCaseInsensitive", Boolean.valueOf(isNameCaseInsensitive).toString());
  }

  public final String getDescriptiveString() {
    StringBuilder sb = new StringBuilder(80);
    if (byName && !isNameCaseInsensitive && !byModifiers && !byType) {
      return " (alphabetized)";
    }
    if (byModifiers || byType || byName) {
      sb.append(" (sorted by");
      boolean needComma = false;
      if (byModifiers) {
        sb.append(" modifiers");
        needComma = true;
      }
      if (byType) {
        if (needComma) {
          sb.append(',');
        }
        sb.append(" type");
        if (isTypeCaseInsensitive) {
          sb.append("(CI)");
        }
        needComma = true;
      }
      if (byName) {
        if (needComma) {
          sb.append(',');
        }
        sb.append(" name");
        if (isNameCaseInsensitive) {
          sb.append("(CI)");
        }
      }
      sb.append(")");
    }
    return sb.toString();
  }

  public final JPanel getSortOptionsPanel() {
    final JPanel abPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    final JLabel sortLabel = new JLabel("Sort");
    constraints.insets = new Insets(0, 5, 0, 5);
    abPanel.add(sortLabel, constraints.firstCol());
    final JCheckBox byModifiersBox = new JCheckBox("by modifiers");
    final JCheckBox byTypeBox = new JCheckBox("by type");
    final JCheckBox typeCIBox = new JCheckBox("case insensitive");
    final JCheckBox byNameBox = new JCheckBox("by name");
    final JCheckBox nameCIBox = new JCheckBox("case insensitive");
    constraints.insets = new Insets(0, 0, 0, 0);
    abPanel.add(byModifiersBox, constraints.weightedLastCol());
    constraints.newRow();
    constraints.nextCol();
    abPanel.add(byTypeBox, constraints.nextCol());
    abPanel.add(typeCIBox, constraints.weightedLastCol());
    constraints.weightedLastRow();
    constraints.nextCol();
    abPanel.add(byNameBox, constraints.nextCol());
    abPanel.add(nameCIBox, constraints.weightedLastCol());

    byModifiersBox.setSelected(isByModifiers());
    byTypeBox.setSelected(isByType());
    typeCIBox.setSelected(isTypeCaseInsensitive());
    byNameBox.setSelected(isByName());
    nameCIBox.setSelected(isNameCaseInsensitive());

    byModifiersBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setByModifiers(byModifiersBox.isSelected());
      }
    });
    byTypeBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setByType(byTypeBox.isSelected());
      }
    });
    typeCIBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setTypeCaseInsensitive(typeCIBox.isSelected());
      }
    });
    byNameBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setByName(byNameBox.isSelected());
      }
    });
    nameCIBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setNameCaseInsensitive(nameCIBox.isSelected());
      }
    });

    return abPanel;
  }

  public String generateSortString(RangeEntry entry) {
    String modifiers = entry.getModifierString();
    String type = entry.getType();
    String name = entry.getName();
    StringBuilder sb = new StringBuilder(modifiers.length() + type.length() + name.length());
    if (byModifiers) {
      sb.append(modifiers);
    }
    if (byType) {
      sb.append(isTypeCaseInsensitive ? type.toLowerCase() : type);
    }
    if (byName) {
      sb.append(isNameCaseInsensitive ? name.toLowerCase() : name);
    }
    return sb.toString();
  }
}
