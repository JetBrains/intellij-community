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
    new AttributesDescriptor("Parameter", PyHighlighter.PY_PARAMETER),
    new AttributesDescriptor("'self' parameter", PyHighlighter.PY_SELF_PARAMETER),
    new AttributesDescriptor("Keyword argument", PyHighlighter.PY_KEYWORD_ARGUMENT),
    new AttributesDescriptor("Function call", PyHighlighter.PY_FUNCTION_CALL),
    new AttributesDescriptor("Method call", PyHighlighter.PY_METHOD_CALL),
    new AttributesDescriptor("Type annotations", PyHighlighter.PY_ANNOTATION),
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
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(PythonFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      "@<decorator>decorator</decorator>(<kwarg>param</kwarg>=1)\n" +
      "def f(<param>x</param>):\n" +
      "    <docComment>\"\"\" Syntax Highlighting Demo\n" +
      "        <docCommentTag>@param</docCommentTag> x Parameter\n" +
      RainbowHighlighter.generatePaletteExample("\n        ") + "\n" +
      "    \"\"\"</docComment>\n" +
      "    <localVar>s</localVar> = (\"Test\", 2+3, {'a': 'b'}, <param>x</param>)   # Comment\n" +
      "    <call>f</call>(<localVar>s</localVar>[0].<mcall>lower()</mcall>)\n"+
      "\n"+
      "class <classDef>Foo</classDef>:\n"+
      "    tags: <annotation>List[<builtin>str</builtin>]</annotation>\n" +
      "    def <predefined>__init__</predefined>(<self>self</self>: <annotation>Foo</annotation>):\n" +
      "        <localVar>byte_string</localVar>: <annotation><builtin>str</builtin></annotation> = 'newline:\\n also newline:\\x0a'\n" +
      "        <localVar>text_string</localVar> = u\"Cyrillic \u042f is \\u042f. Oops: \\u042g\"\n"+
      "        <self>self</self>.<mcall>makeSense</mcall>(<kwarg>whatever</kwarg>=1)\n" +
      "    \n" +
      "    def <funcDef>makeSense</funcDef>(<self>self</self>, <param>whatever</param>):\n"+
      "        <self>self</self>.sense = <param>whatever</param>\n"+
      "\n"+
      "<localVar>x</localVar> = <builtin>len</builtin>('abc')\n"+
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
