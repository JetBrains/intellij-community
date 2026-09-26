/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.plugins.intelliLang.inject.config;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.intellij.plugins.intelliLang.util.StringMatcher;
import org.jetbrains.annotations.NotNull;

import static org.intellij.plugins.intelliLang.inject.InjectorUtils.appendStringPattern;

public class XmlAttributeInjection extends AbstractTagInjection {

  private @NotNull @NlsSafe StringMatcher myAttributeNameMatcher = StringMatcher.NONE;
  private @NotNull @NlsSafe String myAttributeNamespace = "";

  public @NotNull String getAttributeName() {
    return myAttributeNameMatcher.getPattern();
  }

  public void setAttributeName(@NotNull String attributeName) {
    myAttributeNameMatcher = StringMatcher.create(attributeName);
  }

  public @NotNull @NlsSafe String getAttributeNamespace() {
    return myAttributeNamespace;
  }

  public void setAttributeNamespace(@NotNull String attributeNamespace) {
    myAttributeNamespace = attributeNamespace;
  }

  public boolean isApplicable(@NotNull XmlAttributeValue value) {
    final PsiElement element = value.getParent();
    return element instanceof XmlAttribute && matches((XmlAttribute)element);
  }

  public @NlsSafe String getGeneratedName() {
    final String tag = getTagName();
    final String attributeName = getAttributeName();
    if (!attributeName.equals(StringMatcher.NONE.getPattern())) {
      if (!tag.isEmpty()) {
        return tag + "/@" + (!attributeName.isEmpty() ? attributeName : "*");
      }
      else {
        return "*/@" + (!attributeName.isEmpty() ? attributeName : "*");
      }
    }
    return attributeName;
  }

  @Override
  public void generatePlaces() {
    if (StringUtil.isEmpty(getDisplayName())) setDisplayName(getGeneratedName());
    setInjectionPlaces(new InjectionPlace(getCompiler().createElementPattern(getPatternString(this), getDisplayName()), true));
  }

  private boolean matches(@NotNull XmlAttribute attr) {
    // mind IDEA-5206
    final boolean b = myAttributeNameMatcher.matches(attr.getLocalName()) &&
                      (attr.getName().indexOf(':') == -1 || myAttributeNamespace.equals(attr.getNamespace())) &&
                      matches(attr.getParent());

    return b && matchXPath(attr);
  }

  @Override
  public XmlAttributeInjection copy() {
    return new XmlAttributeInjection().copyFrom(this);
  }

  @Override
  public XmlAttributeInjection copyFrom(@NotNull BaseInjection o) {
    super.copyFrom(o);
    if (o instanceof XmlAttributeInjection other) {
      setApplyToSubTags(other.isApplyToSubTags());
      setAttributeName(other.getAttributeName());
      setAttributeNamespace(other.getAttributeNamespace());
    }
    return this;
  }

  @Override
  @SuppressWarnings({"RedundantIfStatement"})
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    final XmlAttributeInjection that = (XmlAttributeInjection)o;

    if (!myAttributeNameMatcher.getPattern().equals(that.myAttributeNameMatcher.getPattern())) return false;
    if (!myAttributeNamespace.equals(that.myAttributeNamespace)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myAttributeNameMatcher.getPattern().hashCode();
    result = 31 * result + myAttributeNamespace.hashCode();
    return result;
  }

  public static String getPatternString(final XmlAttributeInjection injection) {
    final String name = injection.getAttributeName();
    final String namespace = injection.getAttributeNamespace();
    final StringBuilder result = new StringBuilder("xmlAttribute()");
    if (StringUtil.isNotEmpty(name)) appendStringPattern(result, ".withLocalName(", name, ")");
    if (StringUtil.isNotEmpty(namespace)) appendStringPattern(result, ".withNamespace(", namespace, ")");
    if (StringUtil.isNotEmpty(injection.getTagName()) || StringUtil.isNotEmpty(injection.getTagNamespace())) {
      result.append(".").append(injection.isApplyToSubTags() ? "inside" : "withParent").append("(")
        .append(XmlTagInjection.getPatternString(injection)).append(")");
    }
    return result.toString();
  }
}
