/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Aug 27, 2002
 * Time: 9:55:06 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.xml.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.XmlUtil;

public abstract class BasicXmlAttributeDescriptor implements XmlAttributeDescriptor {
  public String validateValue(XmlElement context, String value) {
    if (isFixed() && getDefaultValue() != null) {
      String defaultValue = getDefaultValue();

      if (!defaultValue.equals(value)) {
        return "Attribute " + getName() + " should have fixed value " + defaultValue;
      }
    }

    if (isEnumerated() && XmlUtil.isSimpleXmlAttributeValue(value)) {
      String[] values = getEnumeratedValues();
      boolean valueWasFound = false;

      for (int i = 0; i < values.length; i++) {
        String enumValue = values[i];

        if (enumValue.equals(value)) {
          valueWasFound = true;
          break;
        }
      }

      if (!valueWasFound) {
        return "Wrong attribute value";
      }
    }

    return null;
  }

  public boolean processDeclarations(PsiElement context, PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastElement, PsiElement place){
    return true;
  }

  public String getName(PsiElement context){
    return getName();
  }
}
