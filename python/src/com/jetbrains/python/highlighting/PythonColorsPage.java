// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting;

import com.google.common.collect.ImmutableMap;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * @author yole
 */
public class PythonColorsPage implements RainbowColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor("Number", PyHighlighter.PY_NUMBER),
    new AttributesDescriptor("Keyword", PyHighlighter.PY_KEYWORD),
    new AttributesDescriptor("Line Comment", PyHighlighter.PY_LINE_COMMENT),

    new AttributesDescriptor("String//Binary (bytes)", PyHighlighter.PY_BYTE_STRING),
    new AttributesDescriptor("String//Text (unicode)", PyHighlighter.PY_UNICODE_STRING),
    new AttributesDescriptor("String//Escape sequence//Valid", PyHighlighter.PY_VALID_STRING_ESCAPE),
    new AttributesDescriptor("String//Escape sequence//Invalid", PyHighlighter.PY_INVALID_STRING_ESCAPE),
    new AttributesDescriptor("String//f-string//Expression braces", PyHighlighter.PY_FSTRING_FRAGMENT_BRACES),
    new AttributesDescriptor("String//f-string//Type conversion", PyHighlighter.PY_FSTRING_FRAGMENT_TYPE_CONVERSION),
    new AttributesDescriptor("String//f-string//Format specifier start", PyHighlighter.PY_FSTRING_FRAGMENT_COLON),

    new AttributesDescriptor("Docstring//Text", PyHighlighter.PY_DOC_COMMENT),
    new AttributesDescriptor("Docstring//Tag", PyHighlighter.PY_DOC_COMMENT_TAG),
    
    new AttributesDescriptor("Braces and Operators//Operation sign", PyHighlighter.PY_OPERATION_SIGN),
    new AttributesDescriptor("Braces and Operators//Parentheses", PyHighlighter.PY_PARENTHS),
    new AttributesDescriptor("Braces and Operators//Brackets", PyHighlighter.PY_BRACKETS),
    new AttributesDescriptor("Braces and Operators//Braces", PyHighlighter.PY_BRACES),
    new AttributesDescriptor("Braces and Operators//Comma", PyHighlighter.PY_COMMA),
    new AttributesDescriptor("Braces and Operators//Dot", PyHighlighter.PY_DOT),
    
    new AttributesDescriptor("Functions//Function definition", PyHighlighter.PY_FUNC_DEFINITION),
    new AttributesDescriptor("Functions//Function call", PyHighlighter.PY_FUNCTION_CALL),
    new AttributesDescriptor("Functions//Method call", PyHighlighter.PY_METHOD_CALL),
    
    new AttributesDescriptor("Parameters//Parameter", PyHighlighter.PY_PARAMETER),
    new AttributesDescriptor("Parameters//'self' parameter", PyHighlighter.PY_SELF_PARAMETER),
    
    new AttributesDescriptor("Keyword argument", PyHighlighter.PY_KEYWORD_ARGUMENT),

    new AttributesDescriptor("Special Names//Definition", PyHighlighter.PY_PREDEFINED_DEFINITION),
    new AttributesDescriptor("Special Names//Usage", PyHighlighter.PY_PREDEFINED_USAGE),
    
    new AttributesDescriptor("Built-in name", PyHighlighter.PY_BUILTIN_NAME),
    new AttributesDescriptor("Decorator", PyHighlighter.PY_DECORATOR),
    new AttributesDescriptor("Class definition", PyHighlighter.PY_CLASS_DEFINITION),
    new AttributesDescriptor("Type annotation", PyHighlighter.PY_ANNOTATION),
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
    .put("self", PyHighlighter.PY_SELF_PARAMETER)
    .put("param", PyHighlighter.PY_PARAMETER)
    .put("kwarg", PyHighlighter.PY_KEYWORD_ARGUMENT)
    .put("call", PyHighlighter.PY_FUNCTION_CALL)
    .put("mcall", PyHighlighter.PY_METHOD_CALL)
    .put("annotation", PyHighlighter.PY_ANNOTATION)
    .put("localVar", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    .putAll(RainbowHighlighter.createRainbowHLM())
    .build();

  @Override
  @NotNull
  public String getDisplayName() {
    return "Python";
  }

  @Override
  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighterFactory factory = SyntaxHighlighterFactory.LANGUAGE_FACTORY.forLanguage(PythonLanguage.getInstance());
    if (factory instanceof PySyntaxHighlighterFactory) {
      return ((PySyntaxHighlighterFactory)factory).getSyntaxHighlighterForLanguageLevel(LanguageLevel.getLatest());
    }
    return factory.getSyntaxHighlighter(null, null);
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      "@<decorator>decorator</decorator>(<kwarg>param</kwarg>=1)\n" +
      "def f(<param>x</param>):\n" +
      "    <docComment>\"\"\" Syntax Highlighting Demo\n" +
      "        <docCommentTag>@param</docCommentTag> x Parameter\n" +
      "" +
      RainbowHighlighter.generatePaletteExample("\n        ") + "\n" +
      "    \"\"\"</docComment>\n" +
      "    <localVar>s</localVar> = (\"Test\", 2+3, {'a': 'b'}, f'{<param>x</param>!s:{\"^10\"}}')   # Comment\n" +
      "    <call>f</call>(<localVar>s</localVar>[0].<mcall>lower()</mcall>)\n" +
      "\n" +
      "class <classDef>Foo</classDef>:\n" +
      "    tags: <annotation>List[<builtin>str</builtin>]</annotation>\n" +
      "    def <predefined>__init__</predefined>(<self>self</self>: <annotation>Foo</annotation>):\n" +
      "        <localVar>byte_string</localVar>: <annotation><builtin>bytes</builtin></annotation> = b'newline:\\n also newline:\\x0a'\n" +
      "        <localVar>text_string</localVar> = u\"Cyrillic Я is \\u042f. Oops: \\u042g\"\n" +
      "        <self>self</self>.<mcall>makeSense</mcall>(<kwarg>whatever</kwarg>=1)\n" +
      "    \n" +
      "    def <funcDef>makeSense</funcDef>(<self>self</self>, <param>whatever</param>):\n" +
      "        <self>self</self>.sense = <param>whatever</param>\n" +
      "\n" +
      "<localVar>x</localVar> = <builtin>len</builtin>('abc')\n" +
      "print(f.<predefinedUsage>__doc__</predefinedUsage>)"
    ;
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }

  @Override
  public DisplayPriority getPriority() {
    return PlatformUtils.isPyCharm() ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }

  @Override
  public boolean isRainbowType(TextAttributesKey type) {
    return PyRainbowVisitor.getHIGHLIGHTING_KEYS().contains(type);
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
