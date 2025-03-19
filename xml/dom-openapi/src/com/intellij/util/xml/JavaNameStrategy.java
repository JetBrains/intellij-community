// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * This strategy decapitalizes property name, e.g. getXmlElementName() will correspond to xmlElementName
 */
public class JavaNameStrategy extends DomNameStrategy {
  public static final Function<String,String> DECAPITALIZE_FUNCTION = s -> StringUtil.decapitalize(s);

  @Override
  public final @NotNull String convertName(@NotNull String propertyName) {
    return StringUtil.decapitalize(propertyName);
  }

  @Override
  public final String splitIntoWords(final String tagName) {
    return StringUtil.join(Arrays.asList(NameUtilCore.nameToWords(tagName)), DECAPITALIZE_FUNCTION, " ");
  }
}
