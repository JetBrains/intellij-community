package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.diff.DiffColors;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;

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

  private final Map<String, TextAttributesKey> DEFAULT_HIGHLIGHTING_RULES = ContainerUtil.newHashMap(
    Pair.create("markup.bold", BOLD),
    Pair.create("markup.italic", ITALIC),
    Pair.create("markup.underline", UNDERLINE),
    Pair.create("markup.heading", HEADING),
    Pair.create("markup.changed", DiffColors.DIFF_MODIFIED),
    Pair.create("markup.inserted", DiffColors.DIFF_INSERTED),
    Pair.create("markup.deleted", DiffColors.DIFF_DELETED),
    Pair.create("comment", DefaultLanguageHighlighterColors.LINE_COMMENT),
    Pair.create("comment.line", DefaultLanguageHighlighterColors.LINE_COMMENT),
    Pair.create("comment.block", DefaultLanguageHighlighterColors.BLOCK_COMMENT),
    Pair.create("comment.documentation", DefaultLanguageHighlighterColors.DOC_COMMENT),
    Pair.create("constant", DefaultLanguageHighlighterColors.CONSTANT),
    Pair.create("constant.number", DefaultLanguageHighlighterColors.NUMBER),
    Pair.create("constant.numeric", DefaultLanguageHighlighterColors.NUMBER),
    Pair.create("constant.character.escape", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE),
    Pair.create("constant.character.entity", DefaultLanguageHighlighterColors.MARKUP_ENTITY),
    Pair.create("invalid", HighlighterColors.BAD_CHARACTER),
    Pair.create("invalid.deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES),
    Pair.create("keyword", DefaultLanguageHighlighterColors.KEYWORD),
    Pair.create("keyword.operator", DefaultLanguageHighlighterColors.OPERATION_SIGN),
    Pair.create("storage", DefaultLanguageHighlighterColors.KEYWORD),
    Pair.create("storage.type", DefaultLanguageHighlighterColors.KEYWORD),
    Pair.create("string", DefaultLanguageHighlighterColors.STRING),
    Pair.create("variable", DefaultLanguageHighlighterColors.LOCAL_VARIABLE),
    Pair.create("variable.parameter", DefaultLanguageHighlighterColors.PARAMETER),
    Pair.create("entity", DefaultLanguageHighlighterColors.IDENTIFIER),
    Pair.create("entity.name", DefaultLanguageHighlighterColors.CLASS_NAME),
    Pair.create("entity.name.class", DefaultLanguageHighlighterColors.CLASS_NAME),
    Pair.create("entity.name.function", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION),
    Pair.create("entity.other.attribute-name", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE),
    Pair.create("punctuation", DefaultLanguageHighlighterColors.DOT),
    Pair.create("punctuation.definition.tag", DefaultLanguageHighlighterColors.MARKUP_TAG),
    Pair.create("support.function", DefaultLanguageHighlighterColors.FUNCTION_CALL),
    Pair.create("support.type", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL),
    Pair.create("meta.tag", DefaultLanguageHighlighterColors.METADATA),
    Pair.create("text source", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR)
  );

  public Set<String> getAllDefaultKeys() {
    return DEFAULT_HIGHLIGHTING_RULES.keySet();
  }

  public TextAttributes getTextAttributes(String selector) {
    return getTextAttributesKey(selector).getDefaultAttributes();
  }

  public TextAttributesKey getTextAttributesKey(String selector) {
    return DEFAULT_HIGHLIGHTING_RULES.getOrDefault(selector, HighlighterColors.TEXT);
  }
}
