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
package com.intellij.psi.filters.getters;

import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 24.11.2003
 * Time: 14:17:59
 * To change this template use Options | File Templates.
 */
public class HtmlAttributeValueGetter extends XmlAttributeValueGetter {
  private final boolean myCaseSensitive;

  public HtmlAttributeValueGetter(boolean _caseSensitive) {
    myCaseSensitive = _caseSensitive;
  }

  @Override
  @Nullable
  @NonNls
  protected String[] addSpecificCompletions(final XmlAttribute attribute) {
    @NonNls String name = attribute.getName();
    final XmlTag tag = attribute.getParent();
    if (tag == null) return null;

    @NonNls String tagName = tag.getName();
    if (!myCaseSensitive) {
      name = name.toLowerCase();
      tagName = tagName.toLowerCase();
    }

    final String namespace = tag.getNamespace();
    if (XmlUtil.XHTML_URI.equals(namespace) || XmlUtil.HTML_URI.equals(namespace)) {

      if ("target".equals(name)) {
        return new String[]{"_blank", "_top", "_self", "_parent"};
      }
      else if ("enctype".equals(name)) {
        return new String[]{"multipart/form-data", "application/x-www-form-urlencoded"};
      }
      else if ("rel".equals(name) || "rev".equals(name)) {
        return new String[]{"alternate", "author", "bookmark", "help", "icon", "license", "next", "nofollow",
          "noreferrer", "prefetch", "prev", "search", "stylesheet", "tag", "start", "contents", "index",
          "glossary", "copyright", "chapter", "section", "subsection", "appendix", "script", "import",
          "apple-touch-icon", "apple-touch-icon-precomposed", "apple-touch-startup-image"};
      }
      else if ("media".equals(name)) {
        return new String[]{ "all", "braille", "embossed", "handheld", "print", "projection", "screen", "speech", "tty", "tv" };
      }
      else if ("language".equals(name)) {
        return new String[]{"JavaScript", "VBScript", "JScript", "JavaScript1.2", "JavaScript1.3", "JavaScript1.4", "JavaScript1.5"};
      }
      else if ("type".equals(name) && "link".equals(tagName)) {
        return new String[]{"text/css", "text/html", "text/plain", "text/xml"};
      }
      else if ("http-equiv".equals(name) && "meta".equals(tagName)) {
        return HtmlUtil.RFC2616_HEADERS;
      }
      else if("content".equals(name) && "meta".equals(tagName) && getAttribute(tag, "name") == null) {
        return HtmlUtil.CONTENT_TYPES;
      }
      else if ("accept".equals(name) && "input".equals(tagName)) {
        return HtmlUtil.CONTENT_TYPES;
      }
      else if("accept-charset".equals(name) || "charset".equals(name)) {
        Charset[] charSets = CharsetToolkit.getAvailableCharsets();
        String[] names = new String[charSets.length];
        for (int i = 0; i < names.length; i++) {
          names[i] = charSets[i].toString();
        }
        return names;
      }
    }

    return null;
  }

  @Nullable
  private XmlAttribute getAttribute(@NotNull XmlTag tag, @NotNull String attributeName) {
    if (myCaseSensitive) {
      return tag.getAttribute(attributeName);
    }

    for (XmlAttribute xmlAttribute : tag.getAttributes()) {
      if (attributeName.equalsIgnoreCase(xmlAttribute.getName())) {
        return xmlAttribute;
      }
    }

    return null;
  }
}
