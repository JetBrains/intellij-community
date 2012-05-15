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

import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiMethod;
import com.wrq.rearranger.ModifierConstants;
import com.wrq.rearranger.entry.RangeEntry;
import com.wrq.rearranger.settings.RearrangerSettings;
import com.wrq.rearranger.settings.atomicAttributes.*;
import com.wrq.rearranger.util.MethodUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Routines to handle method modifiers other than those supported by CommonAttributes.  These are the 'abstract'
 * modifier, a type discriminator (constructor, getter/setter, other), and a boolean flag indicating whether the
 * method is overridden or not.
 */
public final class MethodAttributes
  extends ItemAttributes
  implements IRestrictMethodExtraction,
             IHasGetterSetterDefinition
{

// ------------------------------ FIELDS ------------------------------

  private AbstractAttribute     abstractAttr;
  private OverriddenAttribute   overriddenAttr;
  private OverridingAttribute   overridingAttr;
  private InitializerAttribute  staticInitAttr;
  private NativeAttribute       nativeAttr;
  private SynchronizedAttribute syncAttr;
  private ReturnTypeAttribute   returnTypeAttr;
  private ImplementedAttribute  implementedAttr;
  private ImplementingAttribute implementingAttr;
  private MinParamsAttribute    minParamsAttr;
  private MaxParamsAttribute    maxParamsAttr;

  private boolean                constructorMethodType;
  private boolean                getterSetterMethodType;
  private boolean                canonicalMethodType;
  private boolean                otherMethodType;
  private boolean                invertMethodType;
  private boolean                noExtractedMethods;
  private GetterSetterDefinition getterSetterDefinition;

// -------------------------- STATIC METHODS --------------------------

  public static /*MethodAttributes*/AttributeGroup readExternal(final Element item) {
    final MethodAttributes result = new MethodAttributes();
    CommonAttributes.readExternal(result, item);
    result.abstractAttr = AbstractAttribute.readExternal(item);
    result.syncAttr = SynchronizedAttribute.readExternal(item);
    result.overriddenAttr = OverriddenAttribute.readExternal(item);
    result.overridingAttr = OverridingAttribute.readExternal(item);
    result.staticInitAttr = InitializerAttribute.readExternal(item);
    result.nativeAttr = NativeAttribute.readExternal(item);
    result.returnTypeAttr = ReturnTypeAttribute.readExternal(item);
    result.implementedAttr = ImplementedAttribute.readExternal(item);
    result.implementingAttr = ImplementingAttribute.readExternal(item);
    result.minParamsAttr = MinParamsAttribute.readExternal(item);
    result.maxParamsAttr = MaxParamsAttribute.readExternal(item);
    final Element me = item.getChild("Misc");

    result.constructorMethodType = RearrangerSettings.getBooleanAttribute(me, "constructorMethod");
    result.getterSetterMethodType = RearrangerSettings.getBooleanAttribute(me, "getterSetter");
    result.canonicalMethodType = RearrangerSettings.getBooleanAttribute(me, "canonicalMethod");
    result.otherMethodType = RearrangerSettings.getBooleanAttribute(me, "otherMethod");
    result.invertMethodType = RearrangerSettings.getBooleanAttribute(me, "invertMethod");
    result.noExtractedMethods = RearrangerSettings.getBooleanAttribute(me, "noExtractedMethods");
    result.getterSetterDefinition = GetterSetterDefinition.readExternal(item);
    return result;
  }

// --------------------------- CONSTRUCTORS ---------------------------

  public MethodAttributes() {
    init();
    getterSetterDefinition = new GetterSetterDefinition();
  }

  private void init() {
    abstractAttr = new AbstractAttribute();
    overriddenAttr = new OverriddenAttribute();
    overridingAttr = new OverridingAttribute();
    staticInitAttr = new InitializerAttribute();
    nativeAttr = new NativeAttribute();
    syncAttr = new SynchronizedAttribute();
    returnTypeAttr = new ReturnTypeAttribute();
    implementedAttr = new ImplementedAttribute();
    implementingAttr = new ImplementingAttribute();
    minParamsAttr = new MinParamsAttribute();
    maxParamsAttr = new MaxParamsAttribute();
  }

  public MethodAttributes(GetterSetterDefinition defaultGetterSetterDefinition) {
    init();
    getterSetterDefinition = defaultGetterSetterDefinition.deepCopy();
  }

// --------------------- GETTER / SETTER METHODS ---------------------

  public AbstractAttribute getAbstractAttr() {
    return abstractAttr;
  }

  public final JPanel getExcludePanel() {
    final JPanel excludePanel = new JPanel(new GridBagLayout());
//        final Border border = BorderFactory.createEtchedBorder();
//        excludePanel.setBorder(border);
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 1.0d;
    constraints.weighty = 0.0d;
    constraints.gridx = constraints.gridy = 0;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    final JCheckBox excludeBox = new JCheckBox("Exclude from extracted method processing");
    excludeBox.setSelected(noExtractedMethods);
    excludeBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        noExtractedMethods = excludeBox.isSelected();
      }
    });
    excludePanel.add(excludeBox, constraints);
    return excludePanel;
  }

  public GetterSetterDefinition getGetterSetterDefinition() {
    return getterSetterDefinition;
  }

  private NativeAttribute getNativeAttr() {
    return nativeAttr;
  }

  private OverriddenAttribute getOverriddenAttr() {
    return overriddenAttr;
  }

  private OverridingAttribute getOverridingAttr() {
    return overridingAttr;
  }

  public ReturnTypeAttribute getReturnTypeAttr() {
    return returnTypeAttr;
  }

  public InitializerAttribute getStaticInitAttr() {
    return staticInitAttr;
  }

  private SynchronizedAttribute getSyncAttr() {
    return syncAttr;
  }

  public ImplementedAttribute getImplementedAttr() {
    return implementedAttr;
  }

  public ImplementingAttribute getImplementingAttr() {
    return implementingAttr;
  }

  public MinParamsAttribute getMinParamsAttr() {
    return minParamsAttr;
  }

  public MaxParamsAttribute getMaxParamsAttr() {
    return maxParamsAttr;
  }

  public boolean isCanonicalMethodType() {
    return canonicalMethodType;
  }

  public void setCanonicalMethodType(boolean canonicalMethodType) {
    this.canonicalMethodType = canonicalMethodType;
  }

  private boolean isConstructorMethodType() {
    return constructorMethodType;
  }

  public final void setConstructorMethodType(final boolean constructorMethodType) {
    this.constructorMethodType = constructorMethodType;
  }

  private boolean isGetterSetterMethodType() {
    return getterSetterMethodType;
  }

  public final void setGetterSetterMethodType(final boolean getterSetterMethodType) {
    this.getterSetterMethodType = getterSetterMethodType;
  }

  private boolean isInvertMethodType() {
    return invertMethodType;
  }

  public void setInvertMethodType(boolean invertMethodType) {
    this.invertMethodType = invertMethodType;
  }

  public boolean isNoExtractedMethods() {
    return noExtractedMethods;
  }

  public void setNoExtractedMethods(boolean noExtractedMethods) {
    this.noExtractedMethods = noExtractedMethods;
  }

  private boolean isOtherMethodType() {
    return otherMethodType;
  }

  public final void setOtherMethodType(final boolean otherMethodType) {
    this.otherMethodType = otherMethodType;
  }

// ------------------------ CANONICAL METHODS ------------------------

  private boolean predicateAdded = false;
  private String  nextPredicate  = null;

  public final String toString() {
    // convert settings to readable English description of the method.
    //
    final StringBuilder sb = new StringBuilder(80);

    sb.append(getProtectionLevelAttributes().getProtectionLevelString());
    sb.append(getStaticAttribute().getDescriptiveString());
    sb.append(getFinalAttribute().getDescriptiveString());
    sb.append(staticInitAttr.getDescriptiveString());
    sb.append(nativeAttr.getDescriptiveString());
    sb.append(syncAttr.getDescriptiveString());

    final int nTypes = (isConstructorMethodType() ? 1 : 0) +
                       (isGetterSetterMethodType() ? 1 : 0) +
                       (isCanonicalMethodType() ? 1 : 0) +
                       (isOtherMethodType() ? 1 : 0);

    int nTypesSeen = 0;
    if (isConstructorMethodType()) {
      sb.append(isInvertMethodType() ? "non-constructor" : "constructor");
      nTypesSeen++;
      if (nTypesSeen < nTypes) {
        sb.append("/");
      }
    }
    if (isGetterSetterMethodType()) {
      sb.append(isInvertMethodType() ? "non-getter/setter" : "getter/setter");
      nTypesSeen++;
      if (nTypesSeen < nTypes) {
        sb.append("/");
      }
    }
    if (isCanonicalMethodType()) {
      sb.append(isInvertMethodType() ? "non-canonical" : "canonical");
      nTypesSeen++;
      if (nTypesSeen < nTypes) {
        sb.append("/");
      }
    }
    if (isOtherMethodType()) {
      sb.append(isInvertMethodType() ? "non-other-type" : "other");
      nTypesSeen++;
      if (nTypesSeen < nTypes) {
        sb.append("/");
      }
    }
    if (nTypes > 0) {
      sb.append(' ');
    }
    sb.append(abstractAttr.getDescriptiveString());
    sb.append(overriddenAttr.getDescriptiveString());
    sb.append(overridingAttr.getDescriptiveString());
    sb.append(implementedAttr.getDescriptiveString());
    sb.append(implementingAttr.getDescriptiveString());

    if (sb.length() == 0) {
      sb.append("all methods");
    }
    else {
      sb.append("methods");
    }
    predicateAdded = false;
    nextPredicate = null;
    if (minParamsAttr.isMatch() || maxParamsAttr.isMatch()) {
      if (!maxParamsAttr.isMatch()) {
        nextPredicate = minParamsAttr.getDescriptiveString();
      }
      else if (!minParamsAttr.isMatch()) {
        nextPredicate = maxParamsAttr.getDescriptiveString();
      }
      else {
        if (minParamsAttr.getValue() == maxParamsAttr.getValue()) {
          nextPredicate = "with " + minParamsAttr.getValue() +
                          (minParamsAttr.getValue() == 1
                           ? " parameter"
                           : " parameters");
        }
        else {
          nextPredicate = "with " + minParamsAttr.getValue() + " to " +
                          maxParamsAttr.getValue() + " parameters";
        }
      }
    }
    if (getNameAttribute().isMatch()) {
      checkPredicate(sb, false);
      nextPredicate = getNameAttribute().getDescriptiveString();
    }
    if (returnTypeAttr.isMatch()) {
      checkPredicate(sb, false);
      nextPredicate = returnTypeAttr.getDescriptiveString();
    }
    checkPredicate(sb, true);
    sb.append(getSortOptions().getDescriptiveString());
    if (noExtractedMethods) {
      sb.append(" (no extracted methods)");
    }
    return sb.toString();
  }

  private void checkPredicate(@NotNull StringBuilder buffer, boolean finalPredicate) {
    if (predicateAdded && nextPredicate != null) {
      buffer.append(',');
      if (finalPredicate) {
        buffer.append(" and");
      }
    }
    if (nextPredicate != null) {
      buffer.append(' ');
      buffer.append(nextPredicate);
      predicateAdded = true;
      nextPredicate = null;
    }
  }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface AttributeGroup ---------------------

  // Start Methods of Interface AttributeGroup
  @NotNull
  public final /*ItemAttributes*/AttributeGroup deepCopy() {
    final MethodAttributes result = new MethodAttributes();
    deepCopyCommonItems(result);
    result.abstractAttr = (AbstractAttribute)abstractAttr.deepCopy();
    result.nativeAttr = (NativeAttribute)nativeAttr.deepCopy();
    result.syncAttr = (SynchronizedAttribute)syncAttr.deepCopy();
    result.constructorMethodType = constructorMethodType;
    result.getterSetterMethodType = getterSetterMethodType;
    result.canonicalMethodType = canonicalMethodType;
    result.otherMethodType = otherMethodType;
    result.invertMethodType = invertMethodType;
    result.overriddenAttr = (OverriddenAttribute)overriddenAttr.deepCopy();
    result.overridingAttr = (OverridingAttribute)overridingAttr.deepCopy();
    result.implementedAttr = (ImplementedAttribute)implementedAttr.deepCopy();
    result.implementingAttr = (ImplementingAttribute)implementingAttr.deepCopy();
    result.staticInitAttr = (InitializerAttribute)staticInitAttr.deepCopy();
    result.returnTypeAttr = (ReturnTypeAttribute)returnTypeAttr.deepCopy();
    result.minParamsAttr = (MinParamsAttribute)minParamsAttr.deepCopy();
    result.maxParamsAttr = (MaxParamsAttribute)maxParamsAttr.deepCopy();
    result.noExtractedMethods = noExtractedMethods;
    result.getterSetterDefinition = getterSetterDefinition.deepCopy();
    return result;
  }

  @SuppressWarnings("unchecked")
  public final void writeExternal(@NotNull final Element parent) {
    final Element me = new Element("Method");
    writeExternalCommonAttributes(me);
    abstractAttr.appendAttributes(me);
    nativeAttr.appendAttributes(me);
    syncAttr.appendAttributes(me);
    overriddenAttr.appendAttributes(me);
    overridingAttr.appendAttributes(me);
    implementedAttr.appendAttributes(me);
    implementingAttr.appendAttributes(me);
    staticInitAttr.appendAttributes(me);
    returnTypeAttr.appendAttributes(me);
    minParamsAttr.appendAttributes(me);
    maxParamsAttr.appendAttributes(me);
    final Element miscElement = new Element("Misc");
    me.getChildren().add(miscElement);
    miscElement.setAttribute("constructorMethod", Boolean.valueOf(constructorMethodType).toString());
    miscElement.setAttribute("getterSetter", Boolean.valueOf(getterSetterMethodType).toString());
    miscElement.setAttribute("canonicalMethod", Boolean.valueOf(canonicalMethodType).toString());
    miscElement.setAttribute("otherMethod", Boolean.valueOf(otherMethodType).toString());
    miscElement.setAttribute("invertMethod", Boolean.valueOf(invertMethodType).toString());
    miscElement.setAttribute("noExtractedMethods", Boolean.valueOf(noExtractedMethods).toString());
    getterSetterDefinition.appendAttributes(me);
    parent.getChildren().add(me);
  }

// -------------------------- OTHER METHODS --------------------------

  public boolean equals(final Object object) {
    if (!(object instanceof MethodAttributes)) return false;
    final MethodAttributes ma = (MethodAttributes)object;
    return (super.equals(object) &&
            abstractAttr.equals(ma.abstractAttr) &&
            overriddenAttr.equals(ma.overriddenAttr) &&
            overridingAttr.equals(ma.overridingAttr) &&
            implementedAttr.equals(ma.implementedAttr) &&
            implementingAttr.equals(ma.implementingAttr) &&
            staticInitAttr.equals(ma.staticInitAttr) &&
            nativeAttr.equals(ma.nativeAttr) &&
            syncAttr.equals(ma.syncAttr) &&
            returnTypeAttr.equals(ma.returnTypeAttr) &&
            minParamsAttr.equals(ma.minParamsAttr) &&
            maxParamsAttr.equals(ma.maxParamsAttr) &&
            isConstructorMethodType() == ma.isConstructorMethodType() &&
            isGetterSetterMethodType() == ma.isGetterSetterMethodType() &&
            isCanonicalMethodType() == ma.isCanonicalMethodType() &&
            isOtherMethodType() == ma.isOtherMethodType() &&
            invertMethodType == ma.invertMethodType &&
            noExtractedMethods == ma.noExtractedMethods &&
            getterSetterDefinition.equals(ma.getterSetterDefinition));
  }

// End Methods of Interface IRule

  public JPanel getMethodAttributes() {
    final JPanel methodPanel = new JPanel(new GridBagLayout());
    final Border border = BorderFactory.createEtchedBorder();
    methodPanel.setBorder(border);
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.gridwidth = 1;
    constraints.gridheight = 1;
    constraints.weightx = 0.0d;
    constraints.weighty = 0.0d;
    constraints.gridx = constraints.gridy = 0;
    methodPanel.add(getProtectionLevelAttributes().getProtectionLevelPanel(), constraints);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = 1;
    constraints.gridx = 1;
    constraints.weightx = 1;
    methodPanel.add(getMethodTypePanel(), constraints);
    constraints.gridy = 1;
    constraints.insets = new Insets(5, 0, 0, 0);
    constraints.gridx = 0;
    constraints.gridheight = 1;
    constraints.gridwidth = 1;
    methodPanel.add(getStaticAttribute().getAndNotPanel(), constraints);
    constraints.gridx++;
    methodPanel.add(getAbstractAttr().getAndNotPanel(), constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    methodPanel.add(getFinalAttribute().getAndNotPanel(), constraints);
    constraints.gridx++;
    methodPanel.add(getSyncAttr().getAndNotPanel(), constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    methodPanel.add(getOverriddenAttr().getAndNotPanel(), constraints);
    constraints.gridx++;
    methodPanel.add(getOverridingAttr().getAndNotPanel(), constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    methodPanel.add(getImplementedAttr().getAndNotPanel(), constraints);
    constraints.gridx++;
    methodPanel.add(getImplementingAttr().getAndNotPanel(), constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    methodPanel.add(getStaticInitAttr().getAndNotPanel(), constraints);
    constraints.gridx++;
    methodPanel.add(getNativeAttr().getAndNotPanel(), constraints);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridx = 0;
    constraints.gridy++;
    methodPanel.add(getMinParamsAttr().getIntegerPanel(), constraints);
    constraints.gridy++;
    methodPanel.add(getMaxParamsAttr().getIntegerPanel(), constraints);
    constraints.gridy++;
    methodPanel.add(getNameAttribute().getStringPanel(), constraints);
    constraints.gridy++;
    methodPanel.add(getReturnTypeAttr().getStringPanel(), constraints);
    constraints.gridy++;
    constraints.insets = new Insets(0, 0, 0, 0);
    methodPanel.add(getExcludePanel(), constraints);
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weighty = 1.0d;
    methodPanel.add(getSortOptions().getSortOptionsPanel(), constraints);
    return methodPanel;
  }

  private JPanel getMethodTypePanel() {
    final JPanel mtPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.gridwidth = 1;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.weightx = 0.0d;
    constraints.weighty = 1.0d;
    constraints.gridx = constraints.gridy = 0;
    final JCheckBox notBox = new JCheckBox("not");
    notBox.setSelected(isInvertMethodType());
    notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
    mtPanel.add(notBox, constraints);
    constraints.gridx++;
    constraints.weightx = 1.0d;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    mtPanel.add(getMethodTypeInnerPanel(), constraints);
    notBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setInvertMethodType(notBox.isSelected());
        notBox.setForeground(notBox.isSelected() ? Color.BLACK : Color.GRAY);
      }
    });

    return mtPanel;
  }

  private JPanel getMethodTypeInnerPanel() {
    final JPanel mtPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints constraints = new GridBagConstraints();
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.gridheight = 1;
    constraints.anchor = GridBagConstraints.WEST;
    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 1.0d;
    constraints.weighty = 0.0d;
    constraints.gridx = constraints.gridy = 0;
    final JCheckBox constructorBox = new JCheckBox("constructor or");
    constructorBox.setSelected(isConstructorMethodType());
    final JCheckBox getterSetterBox = new JCheckBox();
    final JButton gsDefButton = new JButton("getter/setter");
    final JLabel gsOrLabel = new JLabel(" or");
    getterSetterBox.setSelected(isGetterSetterMethodType());
    final JCheckBox canonicalBox = new JCheckBox("canonical or");
    canonicalBox.setSelected(isCanonicalMethodType());
    final JCheckBox otherTypeBox = new JCheckBox("other type");
    otherTypeBox.setSelected(isOtherMethodType());
    mtPanel.add(constructorBox, constraints);
    constraints.gridy++;
    constraints.weightx = 0;
    constraints.gridwidth = 1;
    mtPanel.add(getterSetterBox, constraints);
    constraints.gridx++;
    mtPanel.add(gsDefButton, constraints);
    constraints.gridx++;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.weightx = 1;
    mtPanel.add(gsOrLabel, constraints);
    constraints.gridx = 0;
    constraints.gridy++;
    mtPanel.add(canonicalBox, constraints);
    constraints.gridy++;
    constraints.gridheight = GridBagConstraints.REMAINDER;
    constraints.weighty = 1.0d;
    mtPanel.add(otherTypeBox, constraints);
    constructorBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setConstructorMethodType(constructorBox.isSelected());
      }
    });
    getterSetterBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setGetterSetterMethodType(getterSetterBox.isSelected());
      }
    });
    canonicalBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setCanonicalMethodType(canonicalBox.isSelected());
      }
    });
    otherTypeBox.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        setOtherMethodType(otherTypeBox.isSelected());
      }
    });
    gsDefButton.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        GetterSetterDefinition tmpDefinition = getterSetterDefinition.deepCopy();
        final JPanel gsDefPanel = tmpDefinition.getGSDefinitionPanel();
        final JOptionPane op = new JOptionPane(
          gsDefPanel,
          JOptionPane.PLAIN_MESSAGE,
          JOptionPane.OK_CANCEL_OPTION,
          null, null, null);
        final JDialog jd = op.createDialog(null, "Getter/Setter definition");
        jd.setVisible(true);
        final Object result = op.getValue();
        if (result != null &&
            ((Integer)result) == JOptionPane.OK_OPTION)
        {
          getterSetterDefinition = tmpDefinition;
        }
      }
    });

    return mtPanel;
  }

// End Methods of Interface AttributeGroup
// Start Methods of Interface IRule

  public final boolean isMatch(@NotNull RangeEntry entry) {
    final boolean result = (entry.getEnd() instanceof PsiMethod ||
                            entry.getEnd() instanceof PsiClassInitializer) &&
                           super.isMatch(entry) &&
                           abstractAttr.isMatch(entry.getModifiers()) &&
                           overriddenAttr.isMatch(entry.getModifiers()) &&
                           overridingAttr.isMatch(entry.getModifiers()) &&
                           implementedAttr.isMatch(entry.getModifiers()) &&
                           implementingAttr.isMatch(entry.getModifiers()) &&
                           staticInitAttr.isMatch(entry.getModifiers()) &&
                           nativeAttr.isMatch(entry.getModifiers()) &&
                           syncAttr.isMatch(entry.getModifiers()) &&
                           returnTypeAttr.isMatch(entry.getType()) &&
                           minParamsAttr.isMatch(entry.getEnd()) &&
                           maxParamsAttr.isMatch(entry.getEnd());
    if (!result) {
      return false;
    }
    boolean typeResult = false;
    if (isConstructorMethodType()) {
      typeResult = (entry.getModifiers() & ModifierConstants.CONSTRUCTOR) == ModifierConstants.CONSTRUCTOR;
    }
    if (isGetterSetterMethodType() &&
        entry.getEnd() instanceof PsiMethod)
    {
      /**
       * determine if the method is a getter or setter according to this rule's specific
       * definition.
       */
      boolean isGetter = MethodUtil.isGetter((PsiMethod)entry.getEnd(),
                                             getterSetterDefinition);
      boolean isSetter = MethodUtil.isSetter((PsiMethod)entry.getEnd(),
                                             getterSetterDefinition);
      typeResult |= isGetter | isSetter;
    }
    if (isCanonicalMethodType()) {
      typeResult |= (entry.getModifiers() & ModifierConstants.CANONICAL) == ModifierConstants.CANONICAL;
    }
    if (isOtherMethodType()) {
      typeResult |= (entry.getModifiers() & ModifierConstants.OTHER_METHOD) == ModifierConstants.OTHER_METHOD;
    }
    typeResult ^= invertMethodType;
    if (!constructorMethodType &&
        !getterSetterMethodType &&
        !canonicalMethodType &&
        !otherMethodType)
    {
      typeResult = true; // true if no method type options are selected.
    }
    return typeResult;
  }
}

