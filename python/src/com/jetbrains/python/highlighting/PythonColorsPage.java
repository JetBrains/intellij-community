// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.highlighting;

import com.google.common.collect.ImmutableMap;
import com.intellij.application.options.colors.InspectionColorSettingsPage;
import com.intellij.codeHighlighting.RainbowHighlighter;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.RainbowColorSettingsPage;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.psi.codeStyle.DisplayPrioritySortable;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;


public class PythonColorsPage implements RainbowColorSettingsPage, InspectionColorSettingsPage, DisplayPrioritySortable {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.number"), PyHighlighter.PY_NUMBER),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.keyword"), PyHighlighter.PY_KEYWORD),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.line.comment"), PyHighlighter.PY_LINE_COMMENT),

    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.binary.bytes"), PyHighlighter.PY_BYTE_STRING),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.text.unicode"), PyHighlighter.PY_UNICODE_STRING),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.escape.sequence.valid"), PyHighlighter.PY_VALID_STRING_ESCAPE),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.escape.sequence.invalid"), PyHighlighter.PY_INVALID_STRING_ESCAPE),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.f.string.expression.braces"), PyHighlighter.PY_FSTRING_FRAGMENT_BRACES),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.f.string.type.conversion"), PyHighlighter.PY_FSTRING_FRAGMENT_TYPE_CONVERSION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.string.f.string.format.specifier.start"), PyHighlighter.PY_FSTRING_FRAGMENT_COLON),

    new AttributesDescriptor(PyBundle.messagePointer("python.colors.docstring.text"), PyHighlighter.PY_DOC_COMMENT),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.docstring.tag"), PyHighlighter.PY_DOC_COMMENT_TAG),
    
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.operation.sign"), PyHighlighter.PY_OPERATION_SIGN),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.parentheses"), PyHighlighter.PY_PARENTHS),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.brackets"), PyHighlighter.PY_BRACKETS),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.braces"), PyHighlighter.PY_BRACES),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.comma"), PyHighlighter.PY_COMMA),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.braces.and.operators.dot"), PyHighlighter.PY_DOT),
    
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.functions.function.definition"), PyHighlighter.PY_FUNC_DEFINITION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.functions.nested.function.definition"), PyHighlighter.PY_NESTED_FUNC_DEFINITION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.functions.function.call"), PyHighlighter.PY_FUNCTION_CALL),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.functions.method.call"), PyHighlighter.PY_METHOD_CALL),
    
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.parameters.parameter"), PyHighlighter.PY_PARAMETER),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.parameters.self.parameter"), PyHighlighter.PY_SELF_PARAMETER),
    
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.keyword.argument"), PyHighlighter.PY_KEYWORD_ARGUMENT),

    new AttributesDescriptor(PyBundle.messagePointer("python.colors.special.names.definition"), PyHighlighter.PY_PREDEFINED_DEFINITION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.special.names.usage"), PyHighlighter.PY_PREDEFINED_USAGE),
    
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.built.in.name"), PyHighlighter.PY_BUILTIN_NAME),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.decorator"), PyHighlighter.PY_DECORATOR),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.class.definition"), PyHighlighter.PY_CLASS_DEFINITION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.type.annotation"), PyHighlighter.PY_ANNOTATION),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.local.variables"), PyHighlighter.PY_LOCAL_VARIABLE),
    new AttributesDescriptor(PyBundle.messagePointer("python.colors.type.parameters"), PyHighlighter.PY_TYPE_PARAMETER),
  };

  @NonNls private static final Map<String,TextAttributesKey> ourTagToDescriptorMap = ImmutableMap.<String, TextAttributesKey>builder()
    .put("docComment", PyHighlighter.PY_DOC_COMMENT)
    .put("docCommentTag", PyHighlighter.PY_DOC_COMMENT_TAG)
    .put("decorator", PyHighlighter.PY_DECORATOR)
    .put("predefined", PyHighlighter.PY_PREDEFINED_DEFINITION)
    .put("predefinedUsage", PyHighlighter.PY_PREDEFINED_USAGE)
    .put("funcDef", PyHighlighter.PY_FUNC_DEFINITION)
    .put("nestedFuncDef", PyHighlighter.PY_NESTED_FUNC_DEFINITION)
    .put("classDef", PyHighlighter.PY_CLASS_DEFINITION)
    .put("builtin", PyHighlighter.PY_BUILTIN_NAME)
    .put("self", PyHighlighter.PY_SELF_PARAMETER)
    .put("param", PyHighlighter.PY_PARAMETER)
    .put("kwarg", PyHighlighter.PY_KEYWORD_ARGUMENT)
    .put("call", PyHighlighter.PY_FUNCTION_CALL)
    .put("mcall", PyHighlighter.PY_METHOD_CALL)
    .put("annotation", PyHighlighter.PY_ANNOTATION)
    .put("localVar", PyHighlighter.PY_LOCAL_VARIABLE)
    .put("typeParam", PyHighlighter.PY_TYPE_PARAMETER)
    .putAll(RainbowHighlighter.createRainbowHLM())
    .build();

  @Override
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("python.colors.python");
  }

  @Override
  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
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
      "    <docComment>\"\"\"\n" +
      "    Syntax Highlighting Demo\n" +
      "    <docCommentTag>@param</docCommentTag> x Parameter\n" +
      RainbowHighlighter.generatePaletteExample("\n    ") + "\n" +
      "    \"\"\"</docComment>\n" +
      "\n" +
      "    def <nestedFuncDef>nested_func</nestedFuncDef>(<param>y</param>):\n" +
      "        <call>print</call>(<param>y</param> + 1)\n" +
      "\n" +
      "    <localVar>s</localVar> = (\"Test\", 2+3, {'a': 'b'}, f'{<param>x</param>!s:{\"^10\"}}')   # Comment\n" +
      "    <call>f</call>(<localVar>s</localVar>[0].<mcall>lower</mcall>())\n" +
      "    <call>nested_func</call>(42)\n" +
      "\n" +
      "class <classDef>Foo</classDef>:\n" +
      "    tags: <annotation>List[<builtin>str</builtin>]</annotation>\n" +
      "\n" +
      "    def <predefined>__init__</predefined>(<self>self</self>: <annotation>Foo</annotation>):\n" +
      "        <localVar>byte_string</localVar>: <annotation><builtin>bytes</builtin></annotation> = b'newline:\\n also newline:\\x0a'\n" +
      "        <localVar>text_string</localVar> = u\"Cyrillic Я is \\u042f. Oops: \\u042g\"\n" +
      "        <self>self</self>.<mcall>make_sense</mcall>(<kwarg>whatever</kwarg>=1)\n" +
      "    \n" +
      "    def <funcDef>make_sense[<typeParam>T</typeParam>]</funcDef>(<self>self</self>, <param>whatever:</param> <annotation>T</annotation>):\n" +
      "        <self>self</self>.sense = <param>whatever</param>\n" +
      "\n" +
      "x = <builtin>len</builtin>('abc')\n" +
      "type my_int< = <builtin>int</builtin>\n" +
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
    return PyRainbowVisitor.Holder.getHIGHLIGHTING_KEYS().contains(type);
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
