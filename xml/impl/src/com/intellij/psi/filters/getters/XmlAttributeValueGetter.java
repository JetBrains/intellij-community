// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters.getters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.NotNull;

public class XmlAttributeValueGetter {
  public static String @NotNull [] getEnumeratedValues(XmlAttribute attribute) {
    final XmlAttributeDescriptor descriptor = attribute.getDescriptor();
    if (descriptor == null) {
      return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    String [] result;
    if (descriptor instanceof BasicXmlAttributeDescriptor) {
      result = ((BasicXmlAttributeDescriptor)descriptor).getEnumeratedValues(attribute);
    }
    else if (descriptor instanceof XmlEnumerationDescriptor) {
      result = ((XmlEnumerationDescriptor<?>)descriptor).getValuesForCompletion();
    }
    else {
      result = descriptor.getEnumeratedValues();
    }
    return result != null ? StringUtil.filterEmptyStrings(result) : ArrayUtilRt.EMPTY_STRING_ARRAY;
  }
}
