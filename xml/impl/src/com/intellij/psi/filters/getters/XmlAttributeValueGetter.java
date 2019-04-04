/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.filters.getters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.NotNull;

public class XmlAttributeValueGetter {
  @NotNull
  public static String[] getEnumeratedValues(XmlAttribute attribute) {
    final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
    if (descriptor == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    
    String [] result;
    if (descriptor instanceof BasicXmlAttributeDescriptor) {
      result = ((BasicXmlAttributeDescriptor)descriptor).getEnumeratedValues(attribute);
    }
    else if (descriptor instanceof XmlEnumerationDescriptor) {
      result = ((XmlEnumerationDescriptor)descriptor).getValuesForCompletion();
    }
    else {
      result = descriptor.getEnumeratedValues();
    }
    return result != null ? StringUtil.filterEmptyStrings(result) : ArrayUtil.EMPTY_STRING_ARRAY;
  }
}
