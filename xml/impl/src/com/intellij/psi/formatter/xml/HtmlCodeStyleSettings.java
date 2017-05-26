/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.formatter.xml;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.WRAP_AS_NEEDED;

public class HtmlCodeStyleSettings extends CustomCodeStyleSettings implements JDOMExternalizable{
  public boolean LEGACY_SETTINGS_IMPORTED = false;
  public boolean HTML_KEEP_WHITESPACES;
  public int HTML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  public int HTML_TEXT_WRAP = WRAP_AS_NEEDED;

  public boolean HTML_KEEP_LINE_BREAKS = true;
  public boolean HTML_KEEP_LINE_BREAKS_IN_TEXT = true;
  public int HTML_KEEP_BLANK_LINES = 2;

  public boolean HTML_ALIGN_ATTRIBUTES = true;
  public boolean HTML_ALIGN_TEXT;

  public boolean HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
  public boolean HTML_SPACE_AFTER_TAG_NAME;
  public boolean HTML_SPACE_INSIDE_EMPTY_TAG;

  @NonNls public String HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = "body,div,p,form,h1,h2,h3";
  @NonNls public String HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = "br";
  @NonNls public String HTML_DO_NOT_INDENT_CHILDREN_OF = "html,body,thead,tbody,tfoot";
  public int HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES;

  @NonNls public String HTML_KEEP_WHITESPACES_INSIDE = "span,pre,textarea";
  @NonNls public String HTML_INLINE_ELEMENTS =
    "a,abbr,acronym,b,basefont,bdo,big,br,cite,cite,code,dfn,em,font,i,img,input,kbd,label,q,s,samp,select,span,strike,strong,sub,sup,textarea,tt,u,var";
  @NonNls public String HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = "title,h1,h2,h3,h4,h5,h6,p";
  public CodeStyleSettings.QuoteStyle HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Double;
  public boolean HTML_ENFORCE_QUOTES = false;
  public CodeStyleSettings.HtmlTagNewLineStyle HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.Never;
  public CodeStyleSettings.HtmlTagNewLineStyle HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.Never;

  public HtmlCodeStyleSettings(CodeStyleSettings container) {
    super("HTML", container);
  }
  
  @SuppressWarnings("unused")
  public HtmlCodeStyleSettings() {
    this(null);
  }

  @SuppressWarnings("deprecation")
  @Override
  protected void importLegacySettings() {
    if (LEGACY_SETTINGS_IMPORTED) {
      return;
    }
    CodeStyleSettings settings = getContainer();
    if (settings == null) {
      return;
    }
    HTML_KEEP_WHITESPACES = getValueOrDefault(settings.HTML_KEEP_WHITESPACES, HTML_KEEP_WHITESPACES);
    HTML_ATTRIBUTE_WRAP = getValueOrDefault(settings.HTML_ATTRIBUTE_WRAP, HTML_ATTRIBUTE_WRAP);
    HTML_TEXT_WRAP = getValueOrDefault(settings.HTML_TEXT_WRAP, HTML_TEXT_WRAP);
    HTML_KEEP_LINE_BREAKS = getValueOrDefault(settings.HTML_KEEP_LINE_BREAKS, HTML_KEEP_LINE_BREAKS);
    HTML_KEEP_LINE_BREAKS_IN_TEXT = getValueOrDefault(settings.HTML_KEEP_LINE_BREAKS_IN_TEXT, HTML_KEEP_LINE_BREAKS_IN_TEXT);
    HTML_KEEP_BLANK_LINES = getValueOrDefault(settings.HTML_KEEP_BLANK_LINES, HTML_KEEP_BLANK_LINES);
    HTML_ALIGN_ATTRIBUTES = getValueOrDefault(settings.HTML_ALIGN_ATTRIBUTES, HTML_ALIGN_ATTRIBUTES);
    HTML_ALIGN_TEXT = getValueOrDefault(settings.HTML_ALIGN_TEXT, HTML_ALIGN_TEXT);
    HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE =
      getValueOrDefault(settings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE, HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE);
    HTML_SPACE_AFTER_TAG_NAME = getValueOrDefault(settings.HTML_SPACE_AFTER_TAG_NAME, HTML_SPACE_AFTER_TAG_NAME);
    HTML_SPACE_INSIDE_EMPTY_TAG = getValueOrDefault(settings.HTML_SPACE_INSIDE_EMPTY_TAG, settings.HTML_SPACE_INSIDE_EMPTY_TAG);
    HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE =
      getValueOrDefault(settings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE, HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE);
    HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE =
      getValueOrDefault(settings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE, HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE);
    HTML_DO_NOT_INDENT_CHILDREN_OF = getValueOrDefault(settings.HTML_DO_NOT_INDENT_CHILDREN_OF, HTML_DO_NOT_INDENT_CHILDREN_OF);
    HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES =
      getValueOrDefault(settings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES, HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES);
    HTML_KEEP_WHITESPACES_INSIDE = getValueOrDefault(settings.HTML_KEEP_WHITESPACES_INSIDE, HTML_KEEP_WHITESPACES_INSIDE);
    HTML_INLINE_ELEMENTS = getValueOrDefault(settings.HTML_INLINE_ELEMENTS, HTML_INLINE_ELEMENTS);
    HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT =
      getValueOrDefault(settings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT, HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT);
    HTML_QUOTE_STYLE = settings.HTML_QUOTE_STYLE;
    HTML_ENFORCE_QUOTES = getValueOrDefault(settings.HTML_ENFORCE_QUOTES, HTML_ENFORCE_QUOTES);
    LEGACY_SETTINGS_IMPORTED = true;
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    final Element childElement = new Element(getTagName());
    DefaultJDOMExternalizer.writeExternal(this, childElement, new DifferenceFilter<>(this, new HtmlCodeStyleSettings()));
    if (!childElement.getContent().isEmpty()) {
      element.addContent(childElement);
    }
  }

  private static String getValueOrDefault(String legacy, String defaultValue) {
    return legacy == null ? defaultValue : legacy;
  }

  private static int getValueOrDefault(int legacy, int defaultValue) {
    return legacy == 0 ? defaultValue : legacy;
  }

  private static boolean getValueOrDefault(boolean legacy, boolean defaultValue) {
    return legacy || defaultValue;
  }
}
