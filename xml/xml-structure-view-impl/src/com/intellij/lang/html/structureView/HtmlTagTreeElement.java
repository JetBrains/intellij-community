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
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

class HtmlTagTreeElement extends PsiTreeElementBase<XmlTag> implements LocationPresentation {
  static final int MAX_TEXT_LENGTH = 50;

  public HtmlTagTreeElement(final XmlTag tag) {
    super(tag);
  }

  @Override
  @NotNull
  public Collection<StructureViewTreeElement> getChildrenBase() {
    final XmlTag tag = getElement();
    if (tag == null || !tag.isValid()) return Collections.emptyList();

    return ContainerUtil.map2List(tag.getSubTags(), new Function<XmlTag, StructureViewTreeElement>() {
      @Override
      public StructureViewTreeElement fun(final XmlTag subTag) {
        return new HtmlTagTreeElement(subTag);
      }
    });
  }

  @Override
  public String getPresentableText() {
    final XmlTag tag = getElement();
    if (tag == null) {
      return IdeBundle.message("node.structureview.invalid");
    }
    return getTagPresentation(tag);
  }

  @Nullable
  @Override
  public String getLocationString() {
    final XmlTag tag = getElement();
    if (tag == null) {
      return null;
    }

    if (tag.getName().equalsIgnoreCase("img") || HtmlUtil.isScriptTag(tag)) {
      return getPathDescription(tag.getAttributeValue("src"));
    }
    else if (tag.getName().equalsIgnoreCase("link")) {
      return getPathDescription(tag.getAttributeValue("href"));
    }
    else {
      return StringUtil.nullize(normalizeSpacesAndShortenIfLong(tag.getValue().getTrimmedText()));
    }
  }

  private static String getPathDescription(String src) {
    if (StringUtil.isEmpty(src)) {
      return null;
    }
    else {
      return StringUtil.shortenPathWithEllipsis(src, MAX_TEXT_LENGTH, true);
    }
  }

  @Override
  public boolean isSearchInLocationString() {
    return true;
  }

  public static String getTagPresentation(final @NotNull XmlTag tag) {
    StringBuilder builder = new StringBuilder(tag.getLocalName());

    String id = XmlTagTreeElement.toCanonicalForm(tag.getAttributeValue(HtmlUtil.ID_ATTRIBUTE_NAME));
    if (id != null) {
      builder.append('#').append(id);
    }

    String classValue = tag.getAttributeValue(HtmlUtil.CLASS_ATTRIBUTE_NAME);
    List<String> classValues = classValue == null ? Collections.<String>emptyList() : StringUtil.split(classValue, " ");
    if (!classValues.isEmpty()) {
      builder.append('.');
      StringUtil.join(classValues, ".", builder);
    }

    return builder.toString();
  }

  @Nullable
  public static String normalizeSpacesAndShortenIfLong(final @NotNull String text) {
    StringBuilder builder = normalizeSpaces(text);
    return builder == null ? null : shortenTextIfLong(builder);
  }

  @Nullable
  private static StringBuilder normalizeSpaces(@NotNull String text) {
    if (text.isEmpty()) {
      return null;
    }

    final StringBuilder buf = new StringBuilder(text.length());
    for (int i = 0, length = text.length(); i < length; i++) {
      char c = text.charAt(i);
      if (c <= ' ' || Character.isSpaceChar(c)) {
        if (buf.length() == 0 || buf.charAt(buf.length() - 1) != ' ') {
          buf.append(' ');
        }
      }
      else {
        buf.append(c);
      }
    }
    return buf;
  }

  @Nullable
  private static String shortenTextIfLong(@NotNull StringBuilder text) {
    if (text.length() <= MAX_TEXT_LENGTH) {
      return text.toString();
    }

    int index;
    for (index = MAX_TEXT_LENGTH; index > MAX_TEXT_LENGTH - 20; index--) {
      if (!Character.isLetter(text.charAt(index))) {
        break;
      }
    }

    text.setLength(Character.isLetter(index) ? MAX_TEXT_LENGTH : index);
    return text.append("\u2026").toString();
  }

  @Override
  public String getLocationPrefix() {
    return "  ";
  }

  @Override
  public String getLocationSuffix() {
    return "";
  }
}
