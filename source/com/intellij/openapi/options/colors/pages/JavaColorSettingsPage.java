/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.options.colors.pages;

import com.intellij.codeInsight.CodeInsightColors;
import com.intellij.debugger.settings.DebuggerColors;
import com.intellij.ide.highlighter.JavaFileHighlighter;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.FileHighlighter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

public class JavaColorSettingsPage implements ColorSettingsPage {
  private final static AttributesDescriptor[] ourDescriptors = new AttributesDescriptor[] {
    new AttributesDescriptor("Keyword", HighlighterColors.JAVA_KEYWORD),
    new AttributesDescriptor("Number", HighlighterColors.JAVA_NUMBER),

    new AttributesDescriptor("String", HighlighterColors.JAVA_STRING),
    new AttributesDescriptor("Valid escape in string", HighlighterColors.JAVA_VALID_STRING_ESCAPE),
    new AttributesDescriptor("Invalid escape in string", HighlighterColors.JAVA_INVALID_STRING_ESCAPE),

    new AttributesDescriptor("Operator sign", HighlighterColors.JAVA_OPERATION_SIGN),
    new AttributesDescriptor("Parentheses", HighlighterColors.JAVA_PARENTHS),
    new AttributesDescriptor("Braces", HighlighterColors.JAVA_BRACES),
    new AttributesDescriptor("Brackets", HighlighterColors.JAVA_BRACKETS),
    new AttributesDescriptor("Comma", HighlighterColors.JAVA_COMMA),
    new AttributesDescriptor("Semicolon", HighlighterColors.JAVA_SEMICOLON),
    new AttributesDescriptor("Dot", HighlighterColors.JAVA_DOT),

    new AttributesDescriptor("Line comment", HighlighterColors.JAVA_LINE_COMMENT),
    new AttributesDescriptor("Block comment", HighlighterColors.JAVA_BLOCK_COMMENT),
    new AttributesDescriptor("JavaDoc comment", HighlighterColors.JAVA_DOC_COMMENT),
    new AttributesDescriptor("JavaDoc tag", HighlighterColors.JAVA_DOC_TAG),
    new AttributesDescriptor("JavaDoc markup", HighlighterColors.JAVA_DOC_MARKUP),

    new AttributesDescriptor("Unknown symbol", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES),
    new AttributesDescriptor("Error", CodeInsightColors.ERRORS_ATTRIBUTES),
    new AttributesDescriptor("Warning", CodeInsightColors.WARNINGS_ATTRIBUTES),
    new AttributesDescriptor("Deprecated symbol", CodeInsightColors.DEPRECATED_ATTRIBUTES),
    new AttributesDescriptor("Unused symbol", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES),

    new AttributesDescriptor("Class", CodeInsightColors.CLASS_NAME_ATTRIBUTES),
    new AttributesDescriptor("Interface", CodeInsightColors.INTERFACE_NAME_ATTRIBUTES),
    new AttributesDescriptor("Local variable", CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES),
    new AttributesDescriptor("Instance field", CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES),
    new AttributesDescriptor("Static field", CodeInsightColors.STATIC_FIELD_ATTRIBUTES),
    new AttributesDescriptor("Parameter", CodeInsightColors.PARAMETER_ATTRIBUTES),
    new AttributesDescriptor("Method call", CodeInsightColors.METHOD_CALL_ATTRIBUTES),
    new AttributesDescriptor("Method declaration", CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor("Constructor call", CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES),
    new AttributesDescriptor("Constructor declaration", CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES),
    new AttributesDescriptor("Static method", CodeInsightColors.STATIC_METHOD_ATTRIBUTES),

    new AttributesDescriptor("Matched brace", CodeInsightColors.MATCHED_BRACE_ATTRIBUTES),
    new AttributesDescriptor("Unmatched brace", CodeInsightColors.UNMATCHED_BRACE_ATTRIBUTES),
    new AttributesDescriptor("Bad character", HighlighterColors.BAD_CHARACTER),

    new AttributesDescriptor("Breakpoint line", DebuggerColors.BREAKPOINT_ATTRIBUTES),
    new AttributesDescriptor("Execution point", DebuggerColors.EXECUTIONPOINT_ATTRIBUTES),

    new AttributesDescriptor("Annotation name", CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES),
    new AttributesDescriptor("Annotation attribute name", CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES)
  };

  private final static ColorDescriptor[] ourColorDescriptors = new ColorDescriptor[]{
    new ColorDescriptor("Method separator color", CodeInsightColors.METHOD_SEPARATORS_COLOR, ColorDescriptor.Kind.FOREGROUND)
  };

  private final static Map<String, TextAttributesKey> ourTags = new HashMap<String, TextAttributesKey>();
  {
    ourTags.put("field", CodeInsightColors.INSTANCE_FIELD_ATTRIBUTES);
    ourTags.put("unusedField", CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES);
    ourTags.put("error", CodeInsightColors.ERRORS_ATTRIBUTES);
    ourTags.put("warning", CodeInsightColors.WARNINGS_ATTRIBUTES);
    ourTags.put("unknownType", CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
    ourTags.put("localVar", CodeInsightColors.LOCAL_VARIABLE_ATTRIBUTES);
    ourTags.put("static", CodeInsightColors.STATIC_FIELD_ATTRIBUTES);
    ourTags.put("deprecated", CodeInsightColors.DEPRECATED_ATTRIBUTES);
    ourTags.put("constructorCall", CodeInsightColors.CONSTRUCTOR_CALL_ATTRIBUTES);
    ourTags.put("constructorDeclaration", CodeInsightColors.CONSTRUCTOR_DECLARATION_ATTRIBUTES);
    ourTags.put("methodCall", CodeInsightColors.METHOD_CALL_ATTRIBUTES);
    ourTags.put("methodDeclaration", CodeInsightColors.METHOD_DECLARATION_ATTRIBUTES);
    ourTags.put("static_method", CodeInsightColors.STATIC_METHOD_ATTRIBUTES);
    ourTags.put("param", CodeInsightColors.PARAMETER_ATTRIBUTES);
    ourTags.put("class", CodeInsightColors.CLASS_NAME_ATTRIBUTES);
    ourTags.put("interface", CodeInsightColors.INTERFACE_NAME_ATTRIBUTES);
    ourTags.put("annotationName", CodeInsightColors.ANNOTATION_NAME_ATTRIBUTES);
    ourTags.put("annotationAttributeName", CodeInsightColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES);
  }

  public String getDisplayName() {
    return "Java";
  }

  public Icon getIcon() {
    return StdFileTypes.JAVA.getIcon();
  }

  public AttributesDescriptor[] getAttributeDescriptors() {
    return ourDescriptors;
  }

  public ColorDescriptor[] getColorDescriptors() {
    return ourColorDescriptors;
  }

  public FileHighlighter getHighlighter() {
    return new JavaFileHighlighter(LanguageLevel.HIGHEST);
  }

  public String getDemoText() {
    String text =
      "/* Block comment */\n" +
      "import <class>java.util.Date</class>;\n" +
      "                               Bad characters: \\n #\n" +
      "/**\n" +
      " * Doc comment here for <code>SomeClass</code>\n" +
      " * @version 1.0\n" +
      " * @see <class>Math</class>#<methodCall>sin</methodCall>(double)\n" +
      " */\n" +
      "<annotationName>@Annotation</annotationName> (<annotationAttributeName>name</annotationAttributeName>=value)\n" +
      "public class <class>SomeClass</class> { // some comment\n" +
      "  private <class>String</class> <field>field</field> = \"Hello World\";\n" +
      "  private double <unusedField>unusedField</unusedField> = 12345.67890;\n" +
      "  private <unknownType>UnknownType</unknownType> <field>anotherString</field> = \"Another\\nStrin\\g\";\n" +
      "  private int[] <field>array</field> = new int[] {1, 2, 3};\n" +
      "  public static int <static>staticField</static> = 0;\n" +
      "\n" +
      "  public <constructorDeclaration>SomeClass</constructorDeclaration>(<interface>AnInterface</interface> <param>param</param>) {\n" +
      "    <todo>//TODO: something</todo>\n" +
      "    <error>int <localVar>localVar</localVar> = \"IntelliJ\"</error>; // Error, incompatible types\n" +
      "    <class>System</class>.<static>out</static>.<methodCall>println</methodCall>(<field>anotherString</field> + <field>field</field> + <localVar>localVar</localVar>);\n" +
      "    long <localVar>time</localVar> = <class>Date</class>.<static_method><deprecated>parse</deprecated></static_method>(\"1.2.3\"); // Method is deprecated\n" +
      "    int <localVar>value</localVar> = this.<warning>staticField</warning>; \n" +
      "  }\n" +
      "  void <methodDeclaration>method</methodDeclaration>() {/*  block\n" +
      "                     comment */\n" +
      "    new <constructorCall>SomeClass</constructorCall>();\n" +
      "  }\n" +
      "}\n" +
      "\n" +
      "interface <interface>AnInterface</interface> {\n" +
      "  int <static>CONSTANT</static> = 2;\n" +
      "}";

    return text;
  }

  public Map<String,TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTags;
  }
}