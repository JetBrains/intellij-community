// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * This strategy splits property name into words, decapitalizes them and joins using hyphen as separator,
 * e.g. getXmlElementName() will correspond to xml-element-name
 */
public class HyphenNameStrategy extends DomNameStrategy {
  @Override
  public @NotNull String convertName(@NotNull String propertyName) {
    return StringUtil.join(NameUtilCore.nameToWordList(propertyName), StringUtil::decapitalize, "-");
  }

  @Override
  public String splitIntoWords(final String tagName) {
    return tagName.replace('-', ' ');
  }
}
