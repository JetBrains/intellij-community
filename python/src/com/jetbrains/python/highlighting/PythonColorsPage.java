package com.jetbrains.python.highlighting;

import com.google.common.collect.ImmutableMap;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * @author yole
 */
public class PythonColorsPage implements ColorSettingsPage, InspectionColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor("Keyword", PyHighlighter.PY_KEYWORD),
    new AttributesDescriptor("String (bytes)", PyHighlighter.PY_BYTE_STRING),
    new AttributesDescriptor("String (Unicode)", PyHighlighter.PY_UNICODE_STRING),
    new AttributesDescriptor("Number", PyHighlighter.PY_NUMBER),
    new AttributesDescriptor("Line Comment", PyHighlighter.PY_LINE_COMMENT),
    new AttributesDescriptor("Operation Sign", PyHighlighter.PY_OPERATION_SIGN),
    new AttributesDescriptor("Parentheses", PyHighlighter.PY_PARENTHS),
    new AttributesDescriptor("Brackets", PyHighlighter.PY_BRACKETS),
    new AttributesDescriptor("Braces", PyHighlighter.PY_BRACES),
    new AttributesDescriptor("Comma", PyHighlighter.PY_COMMA),
    new AttributesDescriptor("Dot", PyHighlighter.PY_DOT),
    new AttributesDescriptor("Function definition", PyHighlighter.PY_FUNC_DEFINITION),
    new AttributesDescriptor("Class definition", PyHighlighter.PY_CLASS_DEFINITION),
    new AttributesDescriptor("Docstring", PyHighlighter.PY_DOC_COMMENT),
    new AttributesDescriptor("Docstring tag", PyHighlighter.PY_DOC_COMMENT_TAG),
    new AttributesDescriptor("Predefined item definition", PyHighlighter.PY_PREDEFINED_DEFINITION),
    new AttributesDescriptor("Decorator", PyHighlighter.PY_DECORATOR),
    new AttributesDescriptor("Built-in name", PyHighlighter.PY_BUILTIN_NAME),
    new AttributesDescriptor("Predefined name", PyHighlighter.PY_PREDEFINED_USAGE),
    new AttributesDescriptor("Valid escape sequence", PyHighlighter.PY_VALID_STRING_ESCAPE),
    new AttributesDescriptor("Invalid escape sequence", PyHighlighter.PY_INVALID_STRING_ESCAPE),
  };

  @NonNls private static final Map<String,TextAttributesKey> ourTagToDescriptorMap = ImmutableMap.<String, TextAttributesKey>builder()
    .put("docComment", PyHighlighter.PY_DOC_COMMENT)
    .put("docCommentTag", PyHighlighter.PY_DOC_COMMENT_TAG)
    .put("decorator", PyHighlighter.PY_DECORATOR)
    .put("predefined", PyHighlighter.PY_PREDEFINED_DEFINITION)
    .put("predefinedUsage", PyHighlighter.PY_PREDEFINED_USAGE)
    .put("funcDef", PyHighlighter.PY_FUNC_DEFINITION)
    .put("classDef", PyHighlighter.PY_CLASS_DEFINITION)
    .put("builtin", PyHighlighter.PY_BUILTIN_NAME)
    .put("unicode", PyHighlighter.PY_UNICODE_STRING)
    .build();

  @NotNull
  public String getDisplayName() {
    return "Python";
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighter.PROVIDER.create(PythonFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @NotNull
  public String getDemoText() {
    return
      "@<decorator>decorator</decorator>(param=1)\n" +
      "def f(x):\n" +
      "    <docComment>\"\"\" Syntax Highlighting Demo\n" +
      "        <docCommentTag>@param</docCommentTag> x Parameter\"\"\"</docComment>\n" +
      "    s = (\"Test\", 2+3, {'a': 'b'}, x)   # Comment\n" +
      "    print s[0].lower()\n"+
      "\n"+
      "class <classDef>Foo</classDef>:\n"+
      "    def <predefined>__init__</predefined>(self):\n" +
      "        self.sense = None\n" +
      "        byte_string = 'newline:\\n also newline:\\x0a'\n" +
      "        text_string = <unicode>u\"Cyrillic \u042f is \\u042f. Oops: </unicode>\\u042g<unicode>\"</unicode>\n"+
      "    \n" +
      "    def <funcDef>makeSense</funcDef>(self, whatever):\n"+
      "        self.sense = whatever\n"+
      "\n"+
      "x = <builtin>len</builtin>('abc')\n"+
      "print(f.<predefinedUsage>__doc__</predefinedUsage>)"
    ;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
