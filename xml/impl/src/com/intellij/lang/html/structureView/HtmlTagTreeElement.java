/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.lang.html.structureView;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.structureView.impl.xml.XmlTagTreeElement;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class HtmlTagTreeElement extends PsiTreeElementBase<XmlTag> implements LocationPresentation {
  static final int MAX_TEXT_LENGTH = 50;

  public HtmlTagTreeElement(final XmlTag tag) {
    super(tag);
  }

  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final XmlTag tag = getElement();
    if (tag == null || !tag.isValid()) return Collections.emptyList();

    return ContainerUtil.map2List(tag.getSubTags(), new Function<XmlTag, StructureViewTreeElement>() {
      public StructureViewTreeElement fun(final XmlTag subTag) {
        return new HtmlTagTreeElement(subTag);
      }
    });
  }

  public String getPresentableText() {
    final XmlTag tag = getElement();
    if (tag == null) return IdeBundle.message("node.structureview.invalid");

    return getTagPresentation(tag);
  }

  public String getLocationString() {
    final XmlTag tag = getElement();
    if (tag == null) return null;

    final String text = normalizeSpacesAndShortenIfLong(tag.getValue().getTrimmedText());
    return text.isEmpty() ? null : text;
  }

  public boolean isSearchInLocationString() {
    return true;
  }

  public static String getTagPresentation(final @NotNull XmlTag tag) {
    final String id = XmlTagTreeElement.toCanonicalForm(tag.getAttributeValue("id"));

    final String classValue = tag.getAttributeValue("class");
    final List<String> classValues = classValue != null ? StringUtil.split(classValue, " ") : Collections.<String>emptyList();

    final StringBuilder text = new StringBuilder(tag.getLocalName());

    if (id != null) {
      text.append("#").append(id);
    }

    if (!classValues.isEmpty()) {
      text.append('.').append(StringUtil.join(classValues, "."));
    }

    return text.toString();
  }

  @NotNull
  public static String normalizeSpacesAndShortenIfLong(final @NotNull String text) {
    return shortenTextIfLong(normalizeSpaces(text));
  }

  private static String normalizeSpaces(final String text) {
    final StringBuilder buf = new StringBuilder();

    for (char ch : text.toCharArray()) {
      if (ch <= ' ' || Character.isSpaceChar(ch)) {
        if (buf.length() == 0 || buf.charAt(buf.length() - 1) != ' ') {
          buf.append(' ');
        }
      }
      else {
        buf.append(ch);
      }
    }

    return buf.toString();
  }

  private static String shortenTextIfLong(final String text) {
    if (text.length() <= MAX_TEXT_LENGTH) return text;

    int index;
    for (index = MAX_TEXT_LENGTH; index > MAX_TEXT_LENGTH - 20; index--) {
      if (!Character.isLetter(text.charAt(index))) {
        break;
      }
    }

    final int endIndex = Character.isLetter(index) ? MAX_TEXT_LENGTH : index;
    return text.substring(0, endIndex) + "...";
  }

  public String getLocationPrefix() {
    return "  ";
  }

  public String getLocationSuffix() {
    return "";
  }
}
