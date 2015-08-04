/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Specifies how method names are converted into XML element names
 *
 * @author peter
 * @see NameStrategy
 * @see NameStrategyForAttributes
 */
public abstract class DomNameStrategy {

  /**
   * @param propertyName property name, i.e. method name without first 'get', 'set' or 'is'
   * @return XML element name
   */
  public abstract String convertName(String propertyName);

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
