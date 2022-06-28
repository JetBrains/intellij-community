package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.awt.*;
import java.util.Map;
import java.util.Set;

public final class TextMateDefaultColorsProvider {
  private final TextAttributesKey BOLD = TextAttributesKey.createTextAttributesKey(
    "markup.bold",
    new TextAttributes(null, null, null, null, Font.BOLD));
  private final TextAttributesKey ITALIC = TextAttributesKey.createTextAttributesKey(
    "markup.italic",
    new TextAttributes(null, null, null, null, Font.ITALIC));
  private final TextAttributesKey UNDERLINE = TextAttributesKey.createTextAttributesKey(
    "markup.underline",
    new TextAttributes(null, null, null, EffectType.LINE_UNDERSCORE, Font.PLAIN));
  private final TextAttributesKey HEADING = TextAttributesKey.createTextAttributesKey(
    "markup.heading",
    new TextAttributes(null, null, null, EffectType.BOLD_LINE_UNDERSCORE, Font.PLAIN));

  private final Map<CharSequence, TextAttributesKey> DEFAULT_HIGHLIGHTING_RULES = Map.ofEntries(
    Map.entry("markup.bold", BOLD),
    Map.entry("markup.italic", ITALIC),
    Map.entry("markup.underline", UNDERLINE),
    Map.entry("markup.heading", HEADING),
    Map.entry("markup.changed", DiffColors.DIFF_MODIFIED),
    Map.entry("markup.inserted", DiffColors.DIFF_INSERTED),
    Map.entry("markup.deleted", DiffColors.DIFF_DELETED),
    Map.entry("comment", DefaultLanguageHighlighterColors.LINE_COMMENT),
    Map.entry("comment.line", DefaultLanguageHighlighterColors.LINE_COMMENT),
    Map.entry("comment.block", DefaultLanguageHighlighterColors.BLOCK_COMMENT),
    Map.entry("comment.documentation", DefaultLanguageHighlighterColors.DOC_COMMENT),
    Map.entry("constant", DefaultLanguageHighlighterColors.CONSTANT),
    Map.entry("constant.number", DefaultLanguageHighlighterColors.NUMBER),
    Map.entry("constant.numeric", DefaultLanguageHighlighterColors.NUMBER),
    Map.entry("constant.character.escape", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE),
    Map.entry("constant.character.entity", DefaultLanguageHighlighterColors.MARKUP_ENTITY),
    Map.entry("invalid", HighlighterColors.BAD_CHARACTER),
    Map.entry("invalid.deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES),
    Map.entry("keyword", DefaultLanguageHighlighterColors.KEYWORD),
    Map.entry("keyword.operator", DefaultLanguageHighlighterColors.OPERATION_SIGN),
    Map.entry("storage", DefaultLanguageHighlighterColors.KEYWORD),
    Map.entry("storage.type", DefaultLanguageHighlighterColors.KEYWORD),
    Map.entry("string", DefaultLanguageHighlighterColors.STRING),
    Map.entry("variable", DefaultLanguageHighlighterColors.LOCAL_VARIABLE),
    Map.entry("variable.parameter", DefaultLanguageHighlighterColors.PARAMETER),
    Map.entry("entity", DefaultLanguageHighlighterColors.IDENTIFIER),
    Map.entry("entity.name", DefaultLanguageHighlighterColors.CLASS_NAME),
    Map.entry("entity.name.class", DefaultLanguageHighlighterColors.CLASS_NAME),
    Map.entry("entity.name.function", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION),
    Map.entry("entity.other.attribute-name", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE),
    Map.entry("punctuation", DefaultLanguageHighlighterColors.DOT),
    Map.entry("punctuation.definition.tag", DefaultLanguageHighlighterColors.MARKUP_TAG),
    Map.entry("support.function", DefaultLanguageHighlighterColors.FUNCTION_CALL),
    Map.entry("support.type", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL),
    Map.entry("meta.tag", DefaultLanguageHighlighterColors.METADATA),
    Map.entry("text source", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR)
  );

  public Set<CharSequence> getAllDefaultKeys() {
    return DEFAULT_HIGHLIGHTING_RULES.keySet();
  }

  public TextAttributesKey getTextAttributesKey(CharSequence selector) {
    return DEFAULT_HIGHLIGHTING_RULES.getOrDefault(selector, HighlighterColors.TEXT);
  }
}
