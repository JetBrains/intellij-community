package com.jetbrains.python;

import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.Map;

/**
 * @author yole
 */
public class PythonColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[] {
      new AttributesDescriptor("Keyword", PyHighlighter.PY_KEYWORD),
      new AttributesDescriptor("String", PyHighlighter.PY_STRING),
      new AttributesDescriptor("Number", PyHighlighter.PY_NUMBER),
      new AttributesDescriptor("Line Comment", PyHighlighter.PY_LINE_COMMENT),
      new AttributesDescriptor("Operation Sign", PyHighlighter.PY_OPERATION_SIGN),
      new AttributesDescriptor("Parentheses", PyHighlighter.PY_PARENTHS),
      new AttributesDescriptor("Brackets", PyHighlighter.PY_BRACKETS),
      new AttributesDescriptor("Braces", PyHighlighter.PY_BRACES),
      new AttributesDescriptor("Comma", PyHighlighter.PY_COMMA),
      new AttributesDescriptor("Dot", PyHighlighter.PY_DOT),
      new AttributesDescriptor("Doc Comment", PyHighlighter.PY_DOC_COMMENT)
  };

  @NonNls private static final HashMap<String,TextAttributesKey> ourTagToDescriptorMap = new HashMap<String, TextAttributesKey>();

  static {
    ourTagToDescriptorMap.put("docComment", PyHighlighter.PY_DOC_COMMENT);
  }

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
    return "def f():\n" +
           "    <docComment>\"\"\" Syntax Highlighting Demo \"\"\"</docComment>\n" +
           "    s = (\"Test\", 2+3, {'a': 'b'})   # Comment\n" +
           "    print s[0].lower()";
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
