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
package com.intellij.patterns;

import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public class XmlPatterns extends PlatformPatterns {
  public static XmlFilePattern.Capture xmlFile() {
    return new XmlFilePattern.Capture();
  }

  public static <T extends XmlAttribute> XmlAttributeValuePattern xmlAttributeValue(ElementPattern<T> attributePattern) {
    for (final PatternCondition<? super T> condition : attributePattern.getCondition().getConditions()) {
      if (condition instanceof PsiNamePatternCondition && "withLocalName".equals(condition.getDebugMethodName())) {
        return xmlAttributeValue().withLocalName(((PsiNamePatternCondition<?>)condition).getNamePattern()).withParent(attributePattern);
      }
    }

    return xmlAttributeValue().withParent(attributePattern);
  }

  public static XmlAttributeValuePattern xmlAttributeValue(String... localNames) {
    return xmlAttributeValue().withLocalName(localNames);
  }

  public static XmlAttributeValuePattern xmlAttributeValue() {
    return new XmlAttributeValuePattern();
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute(@NonNls String localName) {
    return xmlAttribute().withLocalName(localName);
  }

  public static XmlNamedElementPattern.XmlAttributePattern xmlAttribute() {
    return new XmlNamedElementPattern.XmlAttributePattern();
  }

  public static XmlTagPattern.Capture xmlTag() {
    return new XmlTagPattern.Capture();
  }

  public static XmlElementPattern.XmlTextPattern xmlText() {
    return new XmlElementPattern.XmlTextPattern();
  }

  public static XmlElementPattern.XmlEntityRefPattern xmlEntityRef() {
    return new XmlElementPattern.XmlEntityRefPattern();
  }
}
