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
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Function;

import java.util.Arrays;

/**
 * This strategy decapitalizes property name, e.g. getXmlElementName() will correspond to xmlElementName
 *
 * @author peter
 */
public class JavaNameStrategy extends DomNameStrategy {
  public static final Function<String,String> DECAPITALIZE_FUNCTION = new Function<String, String>() {
    @Override
    public String fun(final String s) {
      return StringUtil.decapitalize(s);
    }
  };

  @Override
  public final String convertName(String propertyName) {
    return StringUtil.decapitalize(propertyName);
  }

  @Override
  public final String splitIntoWords(final String tagName) {
    return StringUtil.join(Arrays.asList(NameUtil.nameToWords(tagName)), DECAPITALIZE_FUNCTION, " ");
  }
}
