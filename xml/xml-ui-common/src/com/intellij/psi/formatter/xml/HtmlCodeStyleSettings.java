// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.xml;

import com.intellij.application.options.codeStyle.properties.CommaSeparatedValues;
import com.intellij.configurationStore.Property;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.WrapConstant;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NonNls;

import static com.intellij.psi.codeStyle.CommonCodeStyleSettings.WRAP_AS_NEEDED;

public class HtmlCodeStyleSettings extends CustomCodeStyleSettings {

  public HtmlCodeStyleSettings(CodeStyleSettings container) {
    super("HTMLCodeStyleSettings", container);
  }

  @Property(externalName = "uniform_indent")
  public boolean HTML_UNIFORM_INDENT = false;

  @Property(externalName = "keep_whitespaces")
  public boolean HTML_KEEP_WHITESPACES;
  @Property(externalName = "attribute_wrap")
  @WrapConstant
  public int HTML_ATTRIBUTE_WRAP = WRAP_AS_NEEDED;
  @Property(externalName = "text_wrap")
  @WrapConstant
  public int HTML_TEXT_WRAP = WRAP_AS_NEEDED;

  @Property(externalName = "keep_line_breaks")
  public boolean HTML_KEEP_LINE_BREAKS = true;
  @Property(externalName = "keep_line_breaks_in_text")
  public boolean HTML_KEEP_LINE_BREAKS_IN_TEXT = true;
  @Property(externalName = "keep_blank_lines")
  public int HTML_KEEP_BLANK_LINES = 2;

  @Property(externalName = "align_attributes")
  public boolean HTML_ALIGN_ATTRIBUTES = true;
  @Property(externalName = "align_text")
  public boolean HTML_ALIGN_TEXT;

  @Property(externalName = "space_around_equality_in_attribute")
  public boolean HTML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
  @Property(externalName = "space_after_tag_name")
  public boolean HTML_SPACE_AFTER_TAG_NAME;
  @Property(externalName = "space_inside_empty_tag")
  public boolean HTML_SPACE_INSIDE_EMPTY_TAG;

  @Property(externalName = "add_new_line_before_tags") @CommaSeparatedValues public @NonNls String HTML_ELEMENTS_TO_INSERT_NEW_LINE_BEFORE = "body,div,p,form,h1,h2,h3";

  @Property(externalName = "remove_new_line_before_tags") @CommaSeparatedValues public @NonNls String HTML_ELEMENTS_TO_REMOVE_NEW_LINE_BEFORE = "br";

  @Property(externalName = "do_not_indent_children_of_tags") @CommaSeparatedValues public @NonNls String HTML_DO_NOT_INDENT_CHILDREN_OF = "html,body,thead,tbody,tfoot";

  @Property(externalName = "do_not_align_children_of_min_lines")
  public int HTML_DO_NOT_ALIGN_CHILDREN_OF_MIN_LINES;

  @Property(externalName = "keep_whitespaces_inside") @CommaSeparatedValues public @NonNls String HTML_KEEP_WHITESPACES_INSIDE = "span,pre,textarea";

  @Property(externalName = "inline_tags") @CommaSeparatedValues public @NonNls String HTML_INLINE_ELEMENTS =
    "a,abbr,acronym,b,basefont,bdo,big,br,cite,cite,code,dfn,em,font,i,img,input,kbd,label,q,s,samp,select,small,span,strike,strong,sub,sup,textarea,tt,u,var";

  @Property(externalName = "do_not_break_if_inline_tags") @CommaSeparatedValues public @NonNls String HTML_DONT_ADD_BREAKS_IF_INLINE_CONTENT = "title,h1,h2,h3,h4,h5,h6,p";

  @Property(externalName = "quote_style")
  public CodeStyleSettings.QuoteStyle HTML_QUOTE_STYLE = CodeStyleSettings.QuoteStyle.Double;
  @Property(externalName = "enforce_quotes")
  public boolean HTML_ENFORCE_QUOTES = false;
  @Property(externalName = "new_line_before_first_attribute")
  public CodeStyleSettings.HtmlTagNewLineStyle HTML_NEWLINE_BEFORE_FIRST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.Never;
  @Property(externalName = "new_line_after_last_attribute")
  public CodeStyleSettings.HtmlTagNewLineStyle HTML_NEWLINE_AFTER_LAST_ATTRIBUTE = CodeStyleSettings.HtmlTagNewLineStyle.Never;
}
