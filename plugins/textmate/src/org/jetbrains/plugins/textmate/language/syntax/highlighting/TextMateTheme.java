package org.jetbrains.plugins.textmate.language.syntax.highlighting;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class TextMateTheme {
  public static final TextMateTheme INSTANCE = new TextMateTheme("IntelliJ");

  private static final TextMateDefaultColorsProvider DEFAULT_COLORS_PROVIDER = new TextMateDefaultColorsProvider();

  private static final Map<CharSequence, CharSequence> EXTENSIONS_MAPPING = new HashMap<CharSequence, CharSequence>() {{
    // XmlHighlighterColors
    put("entity.other.attribute-name.localname.xml", "XML_ATTRIBUTE_NAME");
    put("entity.name.tag.xml", "XML_TAG_NAME");
    put("comment.block.html", "HTML_COMMENT");
    put("entity.name.tag", "HTML_TAG_NAME");
    put("entity.other.attribute-name.html", "HTML_ATTRIBUTE_NAME");

    // PyHighlighter
    put("entity.name.function.decorator", "PY.DECORATOR");

    // CSS
    put("entity.other.attribute-name.class.css", "CSS.IDENT");
    put("comment.block.css", "CSS.COMMENT");
    put("support.type.property-name", "CSS.PROPERTY_NAME");
    put("meta.property-value.css", "CSS.PROPERTY_VALUE");
    put("entity.name.tag.css", "CSS.TAG_NAME");
    put("constant.numeric.css", "CSS.NUMBER");
    put("support.function.misc.css", "CSS.FUNCTION");
    put("variable.parameter.misc.css", "CSS.URL");

    // LESS
    put("variable.other.less", "LESS_VARIABLE");
    put("source.css.less", "LESS_JS_CODE_DELIM");
    put("source.js.embedded.less", "LESS_INJECTED_CODE");

    // SASS
    put("variable.parameter.sass", "SASS_VARIABLE");
    put("string.quoted.double.css", "SASS_STRING");
    put("keyword.control.at-rule.css", "SASS_KEYWORD");
    put("support.type.property-name.css", "SASS_PROPERTY_NAME");
    put("meta.selector.css entity.name.tag", "SASS_TAG_NAME");
    put("support.constant.property-value.css", "SASS_FUNCTION");
    put("entity.other.attribute-name.tag", "SASS_MIXIN");

    // JS
    put("string.regexp", "JS.REGEXP");

    // YAML
    put("comment.line.number-sign.yaml", "YAML_COMMENT");
    put("entity.name.tag.yaml", "YAML_SCALAR_KEY");
    put("string.unquoted.block.yaml", "YAML_SCALAR_VALUE");
    put("string.quoted.single.yaml", "YAML_SCALAR_STRING");
    put("string.quoted.double.yaml", "YAML_SCALAR_DSTRING");
    put("string.unquoted.yaml", "YAML_TEXT");

    // Puppet
    put("comment.block.puppet", "PUPPET_BLOCK_COMMENT");
    put("punctuation.definition.variable.puppet", "PUPPET_VARIABLE");
    put("string source", "PUPPET_VARIABLE_INTERPOLATION");
    put("keyword.control.puppet", "PUPPET_KEYWORD");
    put("string.quoted.double.puppet", "PUPPET_STRING");
    put("string.quoted.single.puppet", "PUPPET_SQ_STRING");
    put("keyword.operator.assignment.puppet", "PUPPET_OPERATION_SIGN");
    put("punctuation.section.scope.puppet", "PUPPET_PARENTH");
    put("punctuation.definition.array.begin.puppet", "PUPPET_BRACKETS");
    put("entity.name.type.class.puppet", "PUPPET_CLASS");

    // RubyHighlighter
    put("punctuation.definition.string.begin.ruby", "RUBY_HEREDOC_ID");
    put("string.unquoted.heredoc.ruby", "RUBY_HEREDOC_CONTENT");
    put("string.quoted.single.ruby", "RUBY_STRING");
    put("string.quoted.double.ruby", "RUBY_INTERPOLATED_STRING");
    put("string.quoted.other.literal.upper.ruby", "RUBY_WORDS");
    put("entity.name.type.class.ruby", "RUBY_CONSTANT_DECLARATION");
    put("variable.other.readwrite.global", "RUBY_GVAR");
    put("variable.other.readwrite.class", "RUBY_CVAR");
    put("variable.other.readwrite.instance", "RUBY_IVAR");
    put("punctuation.separator.object", "RUBY_COMMA");
    put("punctuation.separator.method", "RUBY_DOT");
    put("punctuation.separator.statement", "RUBY_SEMICOLON");
    put("punctuation.separator.key-value", "RUBY_HASH_ASSOC");
    put("constant.other.symbol", "RUBY_SYMBOL");

    // ERB
    put("punctuation.section.embedded.ruby", "RHTML_SCRIPTLET_START_ID");
    put("comment.block.erb", "RHTML_COMMENT_ID");
    put("source.ruby.rails.embedded.html", "RHTML_SCRIPTING_BACKGROUND_ID");

    // HAML
    put("text.haml", "HAML_TEXT");
    put("entity.name.tag.class.haml", "HAML_CLASS");
    put("entity.name.tag.id.haml", "HAML_ID");
    put("punctuation.definition.tag.haml", "HAML_TAG");
    put("meta.tag.haml", "HAML_TAG_NAME");
    put("comment.line.slash.haml", "HAML_COMMENT");
    put("meta.prolog.haml", "HAML_XHTML");
    put("source.ruby.embedded.haml", "HAML_RUBY_CODE");
    put("meta.line.ruby.haml", "HAML_RUBY_START");
    put("string.quoted.single.haml", "HAML_STRING");
    put("string.quoted.double.haml", "HAML_STRING_INTERPOLATED");

    // SLIM
    put("text.slim", "SLIM_STATIC_CONTENT");
    put("entity.name.tag.slim", "SLIM_TAG");
    put("punctuation.definition.tag.slim", "SLIM_TAG_START");
    put("comment.line.slash.slim", "SLIM_COMMENT");
    put("meta.prolog.slim", "SLIM_DOCTYPE_KWD");
    put("source.ruby.embedded.slim", "SLIM_RUBY_CODE");
    put("meta.line.ruby.slim", "SLIM_CALL");
    put("invalid.illegal.bad-ampersand.html", "SLIM_BAD_CHARACTER");
    put("string.quoted.double.htm", "SLIM_STRING_INTERPOLATED");

    // Cucumber (Gherkin)
    put("text.gherkin.feature", "GHERKIN_TEXT");
    put("comment.line.number-sign", "GHERKIN_COMMENT");
    put("keyword.language.gherkin.feature", "GHERKIN_KEYWORD");
    put("storage.type.tag.cucumber", "GHERKIN_TAG");
    put("keyword.control.cucumber.table", "GHERKIN_TABLE_PIPE");

    // CoffeeScript
    put("comment.block.coffee", "COFFEESCRIPT.BLOCK_COMMENT");
    put("comment.line.coffee", "COFFEESCRIPT.LINE_COMMENT");
    put("punctuation.terminator.statement.coffee", "COFFEESCRIPT.SEMICOLON");
    put("meta.delimiter.object.comma.coffee", "COFFEESCRIPT.COMMA");
    put("meta.delimiter.method.period.coffee", "COFFEESCRIPT.DOT");
    put("entity.name.function.coffee", "COFFEESCRIPT.CLASS_NAME");
    put("source.coffee", "COFFEESCRIPT.IDENTIFIER");
    put("variable.assignment.coffee", "COFFEESCRIPT.OBJECT_KEY");
    put("constant.numeric.coffee", "COFFEESCRIPT.NUMBER");
    put("constant.language.boolean", "COFFEESCRIPT.BOOLEAN");
    put("punctuation.definition.string.begin.coffee", "COFFEESCRIPT.STRING_LITERAL");
    put("string.quoted.single.coffee", "COFFEESCRIPT.STRING");
    put("string.quoted.double.heredoc.coffee", "COFFEESCRIPT.HEREDOC_CONTENT");
    put("string.regexp.coffee", "COFFEESCRIPT.REGULAR_EXPRESSION_CONTENT");
    put("punctuation.section.embedded.coffee", "COFFEESCRIPT.EXPRESSIONS_SUBSTITUTION_MARK");
    put("meta.brace.round.coffee", "COFFEESCRIPT.PARENTHESIS");
    put("meta.brace.square.coffee", "COFFEESCRIPT.BRACKET");
    put("meta.brace.curly.coffee", "COFFEESCRIPT.BRACE");
    put("keyword.operator.coffee", "COFFEESCRIPT.OPERATIONS");
    put("keyword.control.coffee", "COFFEESCRIPT.KEYWORD");
    put("variable.language.coffee", "COFFEESCRIPT.THIS");
    put("storage.type.function.coffee", "COFFEESCRIPT.FUNCTION");
    put("constant.character.escape.coffee", "COFFEESCRIPT.ESCAPE_SEQUENCE");
    put("string.quoted.script.coffee", "COFFEESCRIPT.JAVASCRIPT_CONTENT");

    // Objective-C Highlighter
    put("keyword.other.directive", "OC.DIRECTIVE");
    put("variable.other.selector.objc", "IVAR");
    put("variable.language.objc", "OC.SELFSUPERTHIS");
    put("meta.implementation.objc", "PROTOCOL_REFERENCE");
    put("variable.parameter.function.objc", "OC.PARAMETER");
  }};

  private static final @NotNull Set<CharSequence> RULES =
    ContainerUtil.union(DEFAULT_COLORS_PROVIDER.getAllDefaultKeys(), EXTENSIONS_MAPPING.keySet());

  @NotNull
  private final String myName;

  private TextMateTheme(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public Color getDefaultBackground() {
    return EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground();
  }

  @NotNull
  public Set<CharSequence> getRules() {
    return RULES;
  }

  @NotNull
  public TextAttributesKey getTextAttributesKey(CharSequence highlightingRule) {
    CharSequence keyName = EXTENSIONS_MAPPING.get(highlightingRule);
    TextAttributesKey extendedKey = keyName != null ? TextAttributesKey.find(keyName.toString()) : null;
    return extendedKey == null ? DEFAULT_COLORS_PROVIDER.getTextAttributesKey(highlightingRule) : extendedKey;
  }
}
