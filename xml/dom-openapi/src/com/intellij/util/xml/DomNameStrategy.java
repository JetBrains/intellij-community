// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import org.jetbrains.annotations.NotNull;

/**
 * Specifies how method names are converted into XML element names
 *
 * @see NameStrategy
 * @see NameStrategyForAttributes
 */
public abstract class DomNameStrategy {

  /**
   * @param propertyName property name, i.e. method name without first 'get', 'set' or 'is'
   * @return XML element name
   */
  public abstract @NotNull String convertName(@NotNull String propertyName);

  /**
   * Is used to get presentable DOM elements in UI  
   * @param xmlElementName XML element name
   * @return Presentable DOM element name
   */
  public abstract String splitIntoWords(final String xmlElementName);

  /**
   * This strategy splits property name into words, decapitalizes them and joins using hyphen as separator,
   * e.g. getXmlElementName() will correspond to xml-element-name
   */
  public static final DomNameStrategy HYPHEN_STRATEGY = new HyphenNameStrategy();
  /**
   * This strategy decapitalizes property name, e.g. getXmlElementName() will correspond to xmlElementName
   */
  public static final DomNameStrategy JAVA_STRATEGY = new JavaNameStrategy();
}
