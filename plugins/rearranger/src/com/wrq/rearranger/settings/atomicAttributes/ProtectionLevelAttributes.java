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

import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.util.Constraints;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Modifier;

/** Routines to handle the four protection level modifiers. */
public final class ProtectionLevelAttributes
  extends AtomicAttribute
{

// ------------------------------ FIELDS ------------------------------

  /** protection levels. */
  private boolean plPublic;
  private boolean plPrivate;
  private boolean plProtected;
  private boolean plPackage;
  private boolean invertProtectionLevel;

// -------------------------- STATIC METHODS --------------------------

  public static ProtectionLevelAttributes readExternal(final Element item) {
    final ProtectionLevelAttributes result = new ProtectionLevelAttributes();
    final Element me = item.getChild("ProtectionLevel");
    result.loadAttributes(me);
    return result;
  }

  private void loadAttributes(final Element me) {
    plPublic = RearrangerSettings.getBooleanAttribute(me, "public");
    plPrivate = RearrangerSettings.getBooleanAttribute(me, "private");
    plProtected = RearrangerSettings.getBooleanAttribute(me, "protected");
    plPackage = RearrangerSettings.getBooleanAttribute(me, "package");
    invertProtectionLevel = RearrangerSettings.getBooleanAttribute(me, "invert");
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  private boolean isInvertProtectionLevel() {
    return invertProtectionLevel;
  }

  public final void setInvertProtectionLevel(final boolean invertProtectionLevel) {
    this.invertProtectionLevel = invertProtectionLevel;
  }

  private boolean isPlPackage() {
    return plPackage;
  }

  public final void setPlPackage(final boolean plPackage) {
    this.plPackage = plPackage;
  }

  private boolean isPlPrivate() {
    return plPrivate;
  }

  public void setPlPrivate(final boolean plPrivate) {
    this.plPrivate = plPrivate;
  }

  private boolean isPlProtected() {
    return plProtected;
  }

  public final void setPlProtected(final boolean plProtected) {
    this.plProtected = plProtected;
  }

  private boolean isPlPublic() {
    return plPublic;
  }

  public final void setPlPublic(final boolean plPublic) {
    this.plPublic = plPublic;
  }

// ------------------------ CANONICAL METHODS ------------------------

  public boolean equals(final Object obj) {
    if (!(obj instanceof ProtectionLevelAttributes)) return false;
    final ProtectionLevelAttributes pla = (ProtectionLevelAttributes)obj;
    return plPublic == pla.plPublic &&
           plPrivate == pla.plPrivate &&
           plProtected == pla.plProtected &&
           plPackage == pla.plPackage &&
           invertProtectionLevel == pla.invertProtectionLevel;
  }

// -------------------------- OTHER METHODS --------------------------

  public final void appendAttributes(final Element me) {
    final Element plElement = new Element("ProtectionLevel");
    me.getChildren().add(plElement);
    plElement.setAttribute("public", Boolean.valueOf(plPublic).toString());
    plElement.setAttribute("private", Boolean.valueOf(plPrivate).toString());
    plElement.setAttribute("protected", Boolean.valueOf(plProtected).toString());
    plElement.setAttribute("package", Boolean.valueOf(plPackage).toString());
    plElement.setAttribute("invert", Boolean.valueOf(invertProtectionLevel).toString());
  }

  public final /*ProtectionLevelAttributes*/AtomicAttribute deepCopy() {
    final ProtectionLevelAttributes pla = new ProtectionLevelAttributes();
    pla.plPublic = plPublic;
    pla.plPrivate = plPrivate;
    pla.plProtected = plProtected;
    pla.plPackage = plPackage;
    pla.invertProtectionLevel = invertProtectionLevel;
    return pla;
  }

  public final JPanel getProtectionLevelPanel() {
    final JPanel plPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    constraints.weightedLastRow();
    constraints.fill = GridBagConstraints.BOTH;
    final JCheckBox notBox = new JCheckBox("not");
    notBox.setSelected(isInvertProtectionLevel());
    notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
    plPanel.add(notBox, constraints.firstCol());
    final JPanel plLevels = getProtLevels();
    plPanel.add(plLevels, constraints.weightedLastCol());
    notBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setInvertProtectionLevel(notBox.isSelected());
        notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
      }
    });

    return plPanel;
  }

  private JPanel getProtLevels() {
    final JPanel plPanel = new JPanel(new GridBagLayout());
    final Constraints constraints = new Constraints();
    final JCheckBox publicBox = new JCheckBox("public or");
    publicBox.setSelected(isPlPublic());
    final JCheckBox privateBox = new JCheckBox("private or");
    privateBox.setSelected(isPlPrivate());
    final JCheckBox protectedBox = new JCheckBox("protected or");
    protectedBox.setSelected(isPlProtected());
    final JCheckBox packageBox = new JCheckBox("package");
    packageBox.setSelected(isPlPackage());
    plPanel.add(publicBox, constraints.weightedLastCol());
    constraints.newRow();
    plPanel.add(privateBox, constraints.weightedLastCol());
    constraints.newRow();
    plPanel.add(protectedBox, constraints.weightedLastCol());
    constraints.weightedLastRow();
    plPanel.add(packageBox, constraints.weightedLastCol());
    publicBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setPlPublic(publicBox.isSelected());
      }
    });
    privateBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setPlPrivate(privateBox.isSelected());
      }
    });
    protectedBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setPlProtected(protectedBox.isSelected());
      }
    });
    packageBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setPlPackage(packageBox.isSelected());
      }
    });
    return plPanel;
  }

  public final String getProtectionLevelString() {
    final StringBuffer sb = new StringBuffer(60);
    final int nProtLevels = (isPlPublic() ? 1 : 0) +
                            (isPlPrivate() ? 1 : 0) +
                            (isPlProtected() ? 1 : 0) +
                            (isPlPackage() ? 1 : 0);

    int nProtLevelsSeen = 0;
    if (isPlPublic()) {
      sb.append(isInvertProtectionLevel() ? "non-public" : "public");
      nProtLevelsSeen++;
      if (nProtLevelsSeen < nProtLevels) {
        sb.append("/");
      }
    }
    if (isPlPrivate()) {
      sb.append(isInvertProtectionLevel() ? "non-private" : "private");
      nProtLevelsSeen++;
      if (nProtLevelsSeen < nProtLevels) {
        sb.append("/");
      }
    }
    if (isPlProtected()) {
      sb.append(isInvertProtectionLevel() ? "non-protected" : "protected");
      nProtLevelsSeen++;
      if (nProtLevelsSeen < nProtLevels) {
        sb.append("/");
      }
    }
    if (isPlPackage()) {
      sb.append(isInvertProtectionLevel() ? "non-package" : "package");
      nProtLevelsSeen++;
      if (nProtLevelsSeen < nProtLevels) {
        sb.append("/");
      }
    }
    if (nProtLevels > 0) {
      sb.append(' ');
    }
    return sb.toString();
  }

  /**
   * @param modifiers Java modifiers for the field, method or class.
   * @return true if the supplied protection level modifiers match this object's modifier requirements.
   */
  public final boolean isMatch(final int modifiers) {
    boolean result;
    if (!plPublic && !plPrivate && !plProtected && !plPackage) {
      return true; // nothing selected, so don't take protection level into account.
    }
    result =
      (plPublic && Modifier.isPublic(modifiers)) ||
      (plPrivate && Modifier.isPrivate(modifiers)) ||
      (plProtected && Modifier.isProtected(modifiers)) ||
      (plPackage && !(Modifier.isPublic(modifiers) ||
                      Modifier.isPrivate(modifiers) ||
                      Modifier.isProtected(modifiers)));
    result ^= invertProtectionLevel;
    return result;
  }
}
