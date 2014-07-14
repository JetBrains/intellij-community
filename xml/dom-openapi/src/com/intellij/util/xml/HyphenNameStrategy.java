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

import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.openapi.util.text.StringUtil;

/**
 * This strategy splits property name into words, decapitalizes them and joins using hyphen as separator,
 * e.g. getXmlElementName() will correspond to xml-element-name
 *
 * @author peter
 */
public class HyphenNameStrategy extends DomNameStrategy {
  @Override
  public String convertName(String propertyName) {
    final String[] words = NameUtil.nameToWords(propertyName);
    for (int i = 0; i < words.length; i++) {
      words[i] = StringUtil.decapitalize(words[i]);
    }
    return StringUtil.join(words, "-");
  }

  @Override
  public String splitIntoWords(final String tagName) {
    return tagName.replace('-', ' ');
  }
}
