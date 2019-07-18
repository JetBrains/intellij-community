package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.google.common.collect.Sets;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TextMateEmulatedTheme extends TextMateTheme {
  public static final TextMateTheme THEME = new TextMateEmulatedTheme();
  private static final Map<String, String> EXTENSIONS_MAPPING = new HashMap<>();
  static {
    // XmlHighlighterColors
    EXTENSIONS_MAPPING.put("entity.other.attribute-name.localname.xml", "XML_ATTRIBUTE_NAME");
    EXTENSIONS_MAPPING.put("entity.name.tag.xml", "XML_TAG_NAME");
    EXTENSIONS_MAPPING.put("comment.block.html", "HTML_COMMENT");
    EXTENSIONS_MAPPING.put("entity.name.tag", "HTML_TAG_NAME");
    EXTENSIONS_MAPPING.put("entity.other.attribute-name.html", "HTML_ATTRIBUTE_NAME");

    // PyHighlighter
    EXTENSIONS_MAPPING.put("entity.name.function.decorator", "PY.DECORATOR");

    // CSS
    EXTENSIONS_MAPPING.put("entity.other.attribute-name.class.css", "CSS.IDENT");
    EXTENSIONS_MAPPING.put("comment.block.css", "CSS.COMMENT");
    EXTENSIONS_MAPPING.put("support.type.property-name", "CSS.PROPERTY_NAME");
    EXTENSIONS_MAPPING.put("meta.property-value.css", "CSS.PROPERTY_VALUE");
    EXTENSIONS_MAPPING.put("entity.name.tag.css", "CSS.TAG_NAME");
    EXTENSIONS_MAPPING.put("constant.numeric.css", "CSS.NUMBER");
    EXTENSIONS_MAPPING.put("support.function.misc.css", "CSS.FUNCTION");
    EXTENSIONS_MAPPING.put("variable.parameter.misc.css", "CSS.URL");

    // LESS
    EXTENSIONS_MAPPING.put("variable.other.less", "LESS_VARIABLE");
    EXTENSIONS_MAPPING.put("source.css.less", "LESS_JS_CODE_DELIM");
    EXTENSIONS_MAPPING.put("source.js.embedded.less", "LESS_INJECTED_CODE");

    // SASS
    EXTENSIONS_MAPPING.put("variable.parameter.sass", "SASS_VARIABLE");
    EXTENSIONS_MAPPING.put("string.quoted.double.css", "SASS_STRING");
    EXTENSIONS_MAPPING.put("keyword.control.at-rule.css", "SASS_KEYWORD");
    EXTENSIONS_MAPPING.put("support.type.property-name.css", "SASS_PROPERTY_NAME");
    EXTENSIONS_MAPPING.put("meta.selector.css entity.name.tag", "SASS_TAG_NAME");
    EXTENSIONS_MAPPING.put("support.constant.property-value.css", "SASS_FUNCTION");
    EXTENSIONS_MAPPING.put("entity.other.attribute-name.tag", "SASS_MIXIN");

    // JS
    EXTENSIONS_MAPPING.put("string.regexp", "JS.REGEXP");

    // YAML
    EXTENSIONS_MAPPING.put("comment.line.number-sign.yaml", "YAML_COMMENT");
    EXTENSIONS_MAPPING.put("entity.name.tag.yaml", "YAML_SCALAR_KEY");
    EXTENSIONS_MAPPING.put("string.unquoted.block.yaml", "YAML_SCALAR_VALUE");
    EXTENSIONS_MAPPING.put("string.quoted.single.yaml", "YAML_SCALAR_STRING");
    EXTENSIONS_MAPPING.put("string.quoted.double.yaml", "YAML_SCALAR_DSTRING");
    EXTENSIONS_MAPPING.put("string.unquoted.yaml", "YAML_TEXT");

    // Puppet
    EXTENSIONS_MAPPING.put("comment.block.puppet", "PUPPET_BLOCK_COMMENT");
    EXTENSIONS_MAPPING.put("punctuation.definition.variable.puppet", "PUPPET_VARIABLE");
    EXTENSIONS_MAPPING.put("string source", "PUPPET_VARIABLE_INTERPOLATION");
    EXTENSIONS_MAPPING.put("keyword.control.puppet", "PUPPET_KEYWORD");
    EXTENSIONS_MAPPING.put("string.quoted.double.puppet", "PUPPET_STRING");
    EXTENSIONS_MAPPING.put("string.quoted.single.puppet", "PUPPET_SQ_STRING");
    EXTENSIONS_MAPPING.put("keyword.operator.assignment.puppet", "PUPPET_OPERATION_SIGN");
    EXTENSIONS_MAPPING.put("punctuation.section.scope.puppet", "PUPPET_PARENTH");
    EXTENSIONS_MAPPING.put("punctuation.definition.array.begin.puppet", "PUPPET_BRACKETS");
    EXTENSIONS_MAPPING.put("entity.name.type.class.puppet", "PUPPET_CLASS");

    // RubyHighlighter
    EXTENSIONS_MAPPING.put("punctuation.definition.string.begin.ruby", "RUBY_HEREDOC_ID");
    EXTENSIONS_MAPPING.put("string.unquoted.heredoc.ruby", "RUBY_HEREDOC_CONTENT");
    EXTENSIONS_MAPPING.put("string.quoted.single.ruby", "RUBY_STRING");
    EXTENSIONS_MAPPING.put("string.quoted.double.ruby", "RUBY_INTERPOLATED_STRING");
    EXTENSIONS_MAPPING.put("string.quoted.other.literal.upper.ruby", "RUBY_WORDS");
    EXTENSIONS_MAPPING.put("entity.name.type.class.ruby", "RUBY_CONSTANT_DECLARATION");
    EXTENSIONS_MAPPING.put("variable.other.readwrite.global", "RUBY_GVAR");
    EXTENSIONS_MAPPING.put("variable.other.readwrite.class", "RUBY_CVAR");
    EXTENSIONS_MAPPING.put("variable.other.readwrite.instance", "RUBY_IVAR");
    EXTENSIONS_MAPPING.put("punctuation.separator.object", "RUBY_COMMA");
    EXTENSIONS_MAPPING.put("punctuation.separator.method", "RUBY_DOT");
    EXTENSIONS_MAPPING.put("punctuation.separator.statement", "RUBY_SEMICOLON");
    EXTENSIONS_MAPPING.put("punctuation.separator.key-value", "RUBY_HASH_ASSOC");
    EXTENSIONS_MAPPING.put("constant.other.symbol", "RUBY_SYMBOL");

    // ERB
    EXTENSIONS_MAPPING.put("punctuation.section.embedded.ruby", "RHTML_SCRIPTLET_START_ID");
    EXTENSIONS_MAPPING.put("comment.block.erb", "RHTML_COMMENT_ID");
    EXTENSIONS_MAPPING.put("source.ruby.rails.embedded.html", "RHTML_SCRIPTING_BACKGROUND_ID");

    // HAML
    EXTENSIONS_MAPPING.put("text.haml", "HAML_TEXT");
    EXTENSIONS_MAPPING.put("entity.name.tag.class.haml", "HAML_CLASS");
    EXTENSIONS_MAPPING.put("entity.name.tag.id.haml", "HAML_ID");
    EXTENSIONS_MAPPING.put("punctuation.definition.tag.haml", "HAML_TAG");
    EXTENSIONS_MAPPING.put("meta.tag.haml", "HAML_TAG_NAME");
    EXTENSIONS_MAPPING.put("comment.line.slash.haml", "HAML_COMMENT");
    EXTENSIONS_MAPPING.put("meta.prolog.haml", "HAML_XHTML");
    EXTENSIONS_MAPPING.put("source.ruby.embedded.haml", "HAML_RUBY_CODE");
    EXTENSIONS_MAPPING.put("meta.line.ruby.haml", "HAML_RUBY_START");
    EXTENSIONS_MAPPING.put("string.quoted.single.haml", "HAML_STRING");
    EXTENSIONS_MAPPING.put("string.quoted.double.haml", "HAML_STRING_INTERPOLATED");

    // SLIM
    EXTENSIONS_MAPPING.put("text.slim", "SLIM_STATIC_CONTENT");
    EXTENSIONS_MAPPING.put("entity.name.tag.slim", "SLIM_TAG");
    EXTENSIONS_MAPPING.put("punctuation.definition.tag.slim", "SLIM_TAG_START");
    EXTENSIONS_MAPPING.put("comment.line.slash.slim", "SLIM_COMMENT");
    EXTENSIONS_MAPPING.put("meta.prolog.slim", "SLIM_DOCTYPE_KWD");
    EXTENSIONS_MAPPING.put("source.ruby.embedded.slim", "SLIM_RUBY_CODE");
    EXTENSIONS_MAPPING.put("meta.line.ruby.slim", "SLIM_CALL");
    EXTENSIONS_MAPPING.put("invalid.illegal.bad-ampersand.html", "SLIM_BAD_CHARACTER");
    EXTENSIONS_MAPPING.put("string.quoted.double.htm", "SLIM_STRING_INTERPOLATED");

    // Cucumber (Gherkin)
    EXTENSIONS_MAPPING.put("text.gherkin.feature", "GHERKIN_TEXT");
    EXTENSIONS_MAPPING.put("comment.line.number-sign", "GHERKIN_COMMENT");
    EXTENSIONS_MAPPING.put("keyword.language.gherkin.feature", "GHERKIN_KEYWORD");
    EXTENSIONS_MAPPING.put("storage.type.tag.cucumber", "GHERKIN_TAG");
    EXTENSIONS_MAPPING.put("keyword.control.cucumber.table", "GHERKIN_TABLE_PIPE");

    // CoffeeScript
    EXTENSIONS_MAPPING.put("comment.block.coffee", "COFFEESCRIPT.BLOCK_COMMENT");
    EXTENSIONS_MAPPING.put("comment.line.coffee", "COFFEESCRIPT.LINE_COMMENT");
    EXTENSIONS_MAPPING.put("punctuation.terminator.statement.coffee", "COFFEESCRIPT.SEMICOLON");
    EXTENSIONS_MAPPING.put("meta.delimiter.object.comma.coffee", "COFFEESCRIPT.COMMA");
    EXTENSIONS_MAPPING.put("meta.delimiter.method.period.coffee", "COFFEESCRIPT.DOT");
    EXTENSIONS_MAPPING.put("entity.name.function.coffee", "COFFEESCRIPT.CLASS_NAME");
    EXTENSIONS_MAPPING.put("source.coffee", "COFFEESCRIPT.IDENTIFIER");
    EXTENSIONS_MAPPING.put("variable.assignment.coffee", "COFFEESCRIPT.OBJECT_KEY");
    EXTENSIONS_MAPPING.put("constant.numeric.coffee", "COFFEESCRIPT.NUMBER");
    EXTENSIONS_MAPPING.put("constant.language.boolean", "COFFEESCRIPT.BOOLEAN");
    EXTENSIONS_MAPPING.put("punctuation.definition.string.begin.coffee", "COFFEESCRIPT.STRING_LITERAL");
    EXTENSIONS_MAPPING.put("string.quoted.single.coffee", "COFFEESCRIPT.STRING");
    EXTENSIONS_MAPPING.put("string.quoted.double.heredoc.coffee", "COFFEESCRIPT.HEREDOC_CONTENT");
    EXTENSIONS_MAPPING.put("string.regexp.coffee", "COFFEESCRIPT.REGULAR_EXPRESSION_CONTENT");
    EXTENSIONS_MAPPING.put("punctuation.section.embedded.coffee", "COFFEESCRIPT.EXPRESSIONS_SUBSTITUTION_MARK");
    EXTENSIONS_MAPPING.put("meta.brace.round.coffee", "COFFEESCRIPT.PARENTHESIS");
    EXTENSIONS_MAPPING.put("meta.brace.square.coffee", "COFFEESCRIPT.BRACKET");
    EXTENSIONS_MAPPING.put("meta.brace.curly.coffee", "COFFEESCRIPT.BRACE");
    EXTENSIONS_MAPPING.put("keyword.operator.coffee", "COFFEESCRIPT.OPERATIONS");
    EXTENSIONS_MAPPING.put("keyword.control.coffee", "COFFEESCRIPT.KEYWORD");
    EXTENSIONS_MAPPING.put("variable.language.coffee", "COFFEESCRIPT.THIS");
    EXTENSIONS_MAPPING.put("storage.type.function.coffee", "COFFEESCRIPT.FUNCTION");
    EXTENSIONS_MAPPING.put("constant.character.escape.coffee", "COFFEESCRIPT.ESCAPE_SEQUENCE");
    EXTENSIONS_MAPPING.put("string.quoted.script.coffee", "COFFEESCRIPT.JAVASCRIPT_CONTENT");

    // Objective-C Highlighter
    EXTENSIONS_MAPPING.put("keyword.other.directive", "OC.DIRECTIVE");
    EXTENSIONS_MAPPING.put("variable.other.selector.objc", "IVAR");
    EXTENSIONS_MAPPING.put("variable.language.objc", "OC.SELFSUPERTHIS");
    EXTENSIONS_MAPPING.put("meta.implementation.objc", "PROTOCOL_REFERENCE");
    EXTENSIONS_MAPPING.put("variable.parameter.function.objc", "OC.PARAMETER");
  }
  public static final Sets.SetView<String> RULES = Sets.union(DEFAULT_COLORS_PROVIDER.getAllDefaultKeys(), EXTENSIONS_MAPPING.keySet());

  protected TextMateEmulatedTheme() {
    super("IntelliJ", new HashMap<>(), new TextAttributes());
  }

  @Override
  public Color getDefaultBackground() {
    return getScheme().getDefaultBackground();
  }

  @NotNull
  private static EditorColorsScheme getScheme() {
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Override
  public Set<String> getRules() {
    return RULES;
  }

  @Override
  public TextAttributesKey getTextAttributesKey(String highlightingRule) {
    String keyName = EXTENSIONS_MAPPING.get(highlightingRule);
    TextAttributesKey extendedKey = keyName != null ? TextAttributesKey.find(keyName) : null;
    return extendedKey == null ? DEFAULT_COLORS_PROVIDER.getTextAttributesKey(highlightingRule) : extendedKey;
  }
}
