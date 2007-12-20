/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 27, 2002
 * Time: 9:55:06 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.Nullable;

public abstract class BasicXmlAttributeDescriptor implements XmlAttributeDescriptor {
  public String validateValue(XmlElement context, String value) {
    if (isFixed()) {
      String defaultValue = getDefaultValue();

      if (defaultValue != null && !defaultValue.equals(value)) {
        return XmlErrorMessages.message("attribute.should.have.fixed.value", getName(), defaultValue);
      }
    }

    if (isEnumerated(context) && XmlUtil.isSimpleXmlAttributeValue(value, (XmlAttributeValue)context)) {
      String[] values = getEnumeratedValues(context);
      boolean valueWasFound = false;

      for (String enumValue : values) {
        if (enumValue.equals(value)) {
          valueWasFound = true;
          break;
        }
      }

      if (!valueWasFound) {
        return XmlErrorMessages.message("wrong.attribute.value");
      }
    }

    return null;
  }

  public String getName(PsiElement context){
    return getName();
  }

  public String[] getEnumeratedValues(@Nullable XmlElement context) {
    return getEnumeratedValues();
  }

  public boolean isEnumerated(@Nullable XmlElement context) {
    return isEnumerated();
  }
}
