// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.highlighter;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class ShColorPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = {
      new AttributesDescriptor(ShBundle.message("sh.color.number"), ShHighlighterColors.NUMBER),
      new AttributesDescriptor(ShBundle.message("sh.color.keyword"), ShHighlighterColors.KEYWORD),
      new AttributesDescriptor(ShBundle.message("sh.color.variables.variable.usage"), ShHighlighterColors.VARIABLE),
      new AttributesDescriptor(ShBundle.message("sh.color.variables.variable.declaration"), ShHighlighterColors.VARIABLE_DECLARATION),
      new AttributesDescriptor(ShBundle.message("sh.color.variables.composed.variable"), ShHighlighterColors.COMPOSED_VARIABLE),
      new AttributesDescriptor(ShBundle.message("sh.color.string"), ShHighlighterColors.STRING),
      new AttributesDescriptor(ShBundle.message("sh.color.raw.string"), ShHighlighterColors.RAW_STRING),
      new AttributesDescriptor(ShBundle.message("sh.color.line.comment"), ShHighlighterColors.LINE_COMMENT),
      new AttributesDescriptor(ShBundle.message("sh.color.shebang.comment"), ShHighlighterColors.SHEBANG_COMMENT),

      new AttributesDescriptor(ShBundle.message("sh.color.here.documents"), ShHighlighterColors.HERE_DOC),
      new AttributesDescriptor(ShBundle.message("sh.color.here.documents.start"), ShHighlighterColors.HERE_DOC_START),
      new AttributesDescriptor(ShBundle.message("sh.color.here.documents.end"), ShHighlighterColors.HERE_DOC_END),

      new AttributesDescriptor(ShBundle.message("sh.color.braces.parentheses"), ShHighlighterColors.PAREN),
      new AttributesDescriptor(ShBundle.message("sh.color.braces.curly.brackets"), ShHighlighterColors.BRACE),
      new AttributesDescriptor(ShBundle.message("sh.color.braces.square.brackets"), ShHighlighterColors.BRACKET),

      new AttributesDescriptor(ShBundle.message("sh.color.backquotes"), ShHighlighterColors.BACKQUOTE),
      new AttributesDescriptor(ShBundle.message("sh.color.redirection"), ShHighlighterColors.REDIRECTION),
      new AttributesDescriptor(ShBundle.message("sh.color.commands.generic.command"), ShHighlighterColors.GENERIC_COMMAND),
      new AttributesDescriptor(ShBundle.message("sh.color.commands.subshell.command"), ShHighlighterColors.SUBSHELL_COMMAND),
      new AttributesDescriptor(ShBundle.message("sh.color.conditional.operators"), ShHighlighterColors.CONDITIONAL_OPERATORS),

      new AttributesDescriptor(ShBundle.message("sh.color.function.declaration"), ShHighlighterColors.FUNCTION_DECLARATION)
  };

  @NonNls private static final HashMap<String, TextAttributesKey> TAG_DESCRIPTOR_MAP = new HashMap<>();
  static {
    TAG_DESCRIPTOR_MAP.put("var", ShHighlighterColors.VARIABLE_DECLARATION);
    TAG_DESCRIPTOR_MAP.put("composed_var", ShHighlighterColors.COMPOSED_VARIABLE);
    TAG_DESCRIPTOR_MAP.put("generic", ShHighlighterColors.GENERIC_COMMAND);
    TAG_DESCRIPTOR_MAP.put("function", ShHighlighterColors.FUNCTION_DECLARATION);
    TAG_DESCRIPTOR_MAP.put("subshell", ShHighlighterColors.SUBSHELL_COMMAND);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return ShLanguage.INSTANCE.getID();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return ShFileType.INSTANCE.getIcon();
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(ShLanguage.INSTANCE, null, null);
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return TAG_DESCRIPTOR_MAP;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDemoText() {
    return
        "#!/usr/bin/env sh\n" +
        "\n" +
        "#Sample comment\n" +
        "<generic>let</generic> \"a=16 << 2\";\n" +
        "<var>b</var>=\"Sample text\";\n" +
        "\n" +
        "function <function>foo</function>() {\n" +
        "  if [ $string1 == $string2 ]; then\n" +
        "    for url in `cat example.txt`; do\n" +
        "      <generic>curl</generic> $url > result.html\n" +
        "    done\n" +
        "  fi\n" +
        "}\n" +
        "\n" +
        "<generic>rm</generic> -f $<subshell>(</subshell>find / -name core<subshell>)</subshell> &> /dev/null\n" +
        "<generic>mkdir</generic> -p \"${<composed_var>AGENT_USER_HOME_</composed_var>${<composed_var>PLATFORM</composed_var>}}\"\n" +
        "\n" +
        "<var>multiline</var>='first line\n" +
        "           second line\n" +
        "           third line'\n" +
        "<generic>cat</generic> << EOF\n" +
        " Sample text\n" +
        "EOF";
  }
}
