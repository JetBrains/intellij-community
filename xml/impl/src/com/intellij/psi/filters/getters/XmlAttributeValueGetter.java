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

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.impl.source.xml.XmlTokenImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import com.intellij.xml.impl.XmlEnumerationDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class XmlAttributeValueGetter implements ContextGetter {
  public XmlAttributeValueGetter() {}

  @Override
  public Object[] get(PsiElement context, CompletionContext completionContext) {
    return getApplicableAttributeVariants(context);
  }

  @Nullable
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
    return result != null ? StringUtil.filterEmptyStrings(result) : null;
  }

  private Object[] getApplicableAttributeVariants(PsiElement _context) {
    if (_context instanceof XmlTokenImpl && ((XmlTokenImpl)_context).getTokenType() == XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN) {
      XmlAttribute attr = PsiTreeUtil.getParentOfType(_context, XmlAttribute.class);
      if (attr != null) {
        final XmlAttributeDescriptor descriptor = attr.getDescriptor();

        if (descriptor != null) {
          if (descriptor.isFixed()) {
            final String defaultValue = descriptor.getDefaultValue();
            return defaultValue == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : new Object[]{defaultValue};
          }

          String[] values = getEnumeratedValues(attr);

          final String[] strings = addSpecificCompletions(attr);

          if (values == null || values.length == 0) {
            values = strings;
          }
          else if (strings != null) {
            values = ArrayUtil.mergeArrays(values, strings);
          }

          return values == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : values;
        }
      }
    }

    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Nullable
  protected String[] addSpecificCompletions(final XmlAttribute context) {
    return null;
  }

}
