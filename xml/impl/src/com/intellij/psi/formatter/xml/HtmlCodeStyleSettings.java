// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.xml;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.WRAP_AS_NEEDED;

public class HtmlCodeStyleSettings extends CustomCodeStyleSettings {

  public HtmlCodeStyleSettings(CodeStyleSettings container) {
    super("HTMLCodeStyleSettings", container);
  }

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

  @SuppressWarnings("deprecation")
  @Override
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
    HTML_KEEP_WHITESPACES = rootSettings.HTML_KEEP_WHITESPACES;
    HTML_ATTRIBUTE_WRAP = rootSettings.HTML_ATTRIBUTE_WRAP;
    HTML_TEXT_WRAP = rootSettings.HTML_TEXT_WRAP;
    HTML_KEEP_LINE_BREAKS = rootSettings.HTML_KEEP_LINE_BREAKS;
    HTML_KEEP_LINE_BREAKS_IN_TEXT = rootSettings.HTML_KEEP_LINE_BREAKS_IN_TEXT;
    HTML_KEEP_BLANK_LINES = rootSettings.HTML_KEEP_BLANK_LINES;
    HTML_ALIGN_ATTRIBUTES = rootSettings.HTML_ALIGN_ATTRIBUTES;
    HTML_ALIGN_TEXT = rootSettings.HTML_ALIGN_TEXT;
    HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = rootSettings.HTML_SPACE_AROUND_EQUALITY_IN_ATTRINUTE;
    HTML_SPACE_AFTER_TAG_NAME = rootSettings.HTML_SPACE_AFTER_TAG_NAME;
    HTML_SPACE_INSIDE_EMPTY_TAG = rootSettings.HTML_SPACE_INSIDE_EMPTY_TAG;
    HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = rootSettings.HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE;
    HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = rootSettings.HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE;
    HTML_DO_NOT_INDENT_CHILDREN_OF = rootSettings.HTML_DO_NOT_INDENT_CHILDREN_OF;
    HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES = rootSettings.HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES;
    HTML_KEEP_WHITESPACES_INSIDE = rootSettings.HTML_KEEP_WHITESPACES_INSIDE;
    HTML_INLINE_ELEMENTS = rootSettings.HTML_INLINE_ELEMENTS;
    HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = rootSettings.HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT;
    HTML_QUOTE_STYLE = rootSettings.HTML_QUOTE_STYLE;
    HTML_ENFORCE_QUOTES = rootSettings.HTML_ENFORCE_QUOTES;
    HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = rootSettings.HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE;
    HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = rootSettings.HTML_NEWLINE_AFTER_LAST_ATTRIBUTE;
  }
}
