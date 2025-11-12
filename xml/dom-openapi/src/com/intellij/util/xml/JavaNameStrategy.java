// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * This strategy decapitalizes property name, e.g. getXmlElementName() will correspond to xmlElementName
 */
public class JavaNameStrategy extends DomNameStrategy {

  @Override
  public final @NotNull String convertName(@NotNull String propertyName) {
    return StringUtil.decapitalize(propertyName);
  }

  @Override
  public final String splitIntoWords(final String tagName) {
    return StringUtil.join(NameUtilCore.nameToWordList(tagName), StringUtil::decapitalize, " ");
  }
}
