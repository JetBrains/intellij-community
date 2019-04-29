package com.intellij.bash.highlighter;

import com.intellij.bash.BashFileType;
import com.intellij.bash.BashLanguage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class BashColorPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = {
      new AttributesDescriptor("Number", BashHighlighterColors.NUMBER),
      new AttributesDescriptor("Keyword", BashHighlighterColors.KEYWORD),
      new AttributesDescriptor("Variables//Variable Usage", BashHighlighterColors.VARIABLE),
      new AttributesDescriptor("Variables//Variable Declaration", BashHighlighterColors.VARIABLE_DECLARATION),
      new AttributesDescriptor("String", BashHighlighterColors.STRING),
      new AttributesDescriptor("Raw String", BashHighlighterColors.RAW_STRING),
      new AttributesDescriptor("Line Comment", BashHighlighterColors.LINE_COMMENT),
      new AttributesDescriptor("Shebang Comment", BashHighlighterColors.SHEBANG_COMMENT),

      new AttributesDescriptor("Here Documents", BashHighlighterColors.HERE_DOC),
      new AttributesDescriptor("Here Documents Start", BashHighlighterColors.HERE_DOC_START),
      new AttributesDescriptor("Here Documents End", BashHighlighterColors.HERE_DOC_END),

      new AttributesDescriptor("Braces//Parentheses", BashHighlighterColors.PAREN),
      new AttributesDescriptor("Braces//Curly Brackets", BashHighlighterColors.BRACE),
      new AttributesDescriptor("Braces//Square Brackets", BashHighlighterColors.BRACKET),

      new AttributesDescriptor("Backquotes", BashHighlighterColors.BACKQUOTE),
      new AttributesDescriptor("Redirection", BashHighlighterColors.REDIRECTION),
      new AttributesDescriptor("Commands//Let Command", BashHighlighterColors.LET_COMMAND),
      new AttributesDescriptor("Commands//Generic Command", BashHighlighterColors.GENERIC_COMMAND),
      new AttributesDescriptor("Commands//Subshell Command", BashHighlighterColors.SUBSHELL_COMMAND),
      new AttributesDescriptor("Conditional operators", BashHighlighterColors.CONDITIONAL_OPERATORS),

      new AttributesDescriptor("Function Declaration", BashHighlighterColors.FUNCTION_DECLARATION)
  };

  private static final HashMap<String, TextAttributesKey> TAG_DESCRIPTOR_MAP = new HashMap<>();
  static {
    TAG_DESCRIPTOR_MAP.put("var", BashHighlighterColors.VARIABLE_DECLARATION);
    TAG_DESCRIPTOR_MAP.put("string", BashHighlighterColors.STRING);
    TAG_DESCRIPTOR_MAP.put("generic", BashHighlighterColors.GENERIC_COMMAND);
    TAG_DESCRIPTOR_MAP.put("function", BashHighlighterColors.FUNCTION_DECLARATION);
    TAG_DESCRIPTOR_MAP.put("subshell", BashHighlighterColors.SUBSHELL_COMMAND);
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return BashLanguage.INSTANCE.getID();
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return BashFileType.INSTANCE.getIcon();
  }

  @NotNull
  @Override
  public SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(BashLanguage.INSTANCE, null, null);
  }

  @Nullable
  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return TAG_DESCRIPTOR_MAP;
  }

  @NotNull
  @Override
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  @Override
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public String getDemoText() {
    return
        "#!/usr/bin/env bash\n" +
        "\n" +
        "#Sample comment\n" +
        "let \"a=16 << 2\";\n" +
        "<var>b</var>=<string>\"Sample text\"</string>;\n" +
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
        "\n" +
        "<var>multiline</var>='first line\n" +
        "           second line\n" +
        "           third line'\n" +
        "cat << EOF\n" +
        " Sample text\n" +
        "EOF";
  }
}
