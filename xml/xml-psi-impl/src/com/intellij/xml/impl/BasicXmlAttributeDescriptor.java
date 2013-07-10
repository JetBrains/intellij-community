/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

  @Nullable
  public String[] getEnumeratedValues(@Nullable XmlElement context) {
    return getEnumeratedValues();
  }

  public boolean isEnumerated(@Nullable XmlElement context) {
    return isEnumerated();
  }

  @Override
  public String toString() {
    return getName();
  }
}
