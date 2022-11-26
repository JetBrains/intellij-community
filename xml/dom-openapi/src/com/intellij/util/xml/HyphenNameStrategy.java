// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * This strategy splits property name into words, decapitalizes them and joins using hyphen as separator,
 * e.g. getXmlElementName() will correspond to xml-element-name
 */
public class HyphenNameStrategy extends DomNameStrategy {
  @NotNull
  @Override
  public String convertName(@NotNull String propertyName) {
    final String[] words = NameUtilCore.nameToWords(propertyName);
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
