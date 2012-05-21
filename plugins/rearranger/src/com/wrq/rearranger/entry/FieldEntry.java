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
package com.wrq.rearranger.entry;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.wrq.rearranger.rearrangement.Emitter;
import com.wrq.rearranger.settings.RearrangerSettings;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/** Represents a member field of a class. */
public class FieldEntry
  extends ClassContentsEntry
{
  /**
   * If Keep Getters/Setters With Property is true, and if this field is a property, then a reference to the
   * property's getter (primary) method is stored here.  That method is removed from the list of entries considered
   * for rule matching.  This field is responsible for emitting that method.
   */
  MethodEntry getterMethod;

  public FieldEntry(final PsiElement start,
                    final PsiElement end,
                    final int modifiers,
                    final String modifierString,
                    final String name,
                    final String type)
  {
    super(start, end, modifiers, modifierString, name, type);
    getterMethod = null;
  }

  public String getTypeIconName() {
    if (myEnd instanceof PsiField) {
      PsiField f = (PsiField)myEnd;
      return ((PsiModifierList)f.getModifierList()).hasModifierProperty(PsiModifier.STATIC) ? "nodes/staticField" : "nodes/field";
    }
    return null;
  }

  public String[] getAdditionalIconNames() {
    if (myEnd instanceof PsiField) {
      PsiField f = (PsiField)myEnd;
      if (f.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) return new String[]{"nodes/c_public"};
      if (f.getModifierList().hasModifierProperty(PsiModifier.PROTECTED)) return new String[]{"nodes/c_protected"};
      if (f.getModifierList().hasModifierProperty(PsiModifier.PRIVATE)) return new String[]{"nodes/c_private"};
      return new String[]{"nodes/c_plocal"};
    }
    return null;
  }

  public JLabel getPopupEntryText(RearrangerSettings settings) {
    StringBuffer name = new StringBuffer(80);
    PsiField f = (PsiField)myEnd;
    name.append(f.getName());
    name.append(": ");
    name.append(f.getTypeElement().getText());
    if (f.getInitializer() != null && f.getInitializer().getText().indexOf('\n') < 0) {
      name.append(" = ");
      name.append(f.getInitializer().getText());
    }
    return new JLabel(name.toString());
  }

  public DefaultMutableTreeNode addToPopupTree(DefaultMutableTreeNode parent, RearrangerSettings settings) {
    DefaultMutableTreeNode node = super.addToPopupTree(parent, settings);
    if (node != null && getterMethod != null) {
      getterMethod.addToPopupTree(node, settings);
      for (MethodEntry me : getterMethod.myCorrespondingGetterSetters) {
        me.addToPopupTree(node, settings);
      }
    }
    return node;
  }

  public void emit(Emitter emitter) {
    super.emit(emitter);
    /**
     * emit setters/getters if they are kept with the property.
     */
    if (getterMethod != null) {
      getterMethod.emit(emitter);
    }
  }

  public MethodEntry getGetterMethod() {
    return getterMethod;
  }

  public void setGetterMethod(MethodEntry getterMethod) {
    this.getterMethod = getterMethod;
  }
}
