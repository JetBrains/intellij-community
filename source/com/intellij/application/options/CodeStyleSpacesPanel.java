package com.intellij.application.options;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;

public class CodeStyleSpacesPanel extends OptionTreeWithPreviewPanel {
  public CodeStyleSpacesPanel(CodeStyleSettings settings) {
    super(settings);
  }

  private static final String AROUND_OPERATORS = "Around Operators";
  private static final String BEFORE_PARENTHESES = "Before Parentheses";
  private static final String BEFORE_LEFT_BRACE = "Before Left Brace";
  private static final String WITHIN_PARENTHESES = "Within Parentheses";
  private static final String TERNARY_OPERATOR = "In Ternary Operator (?:)";
  private static final String OTHER = "Other";

  protected void initTables() {
    initBooleanField("SPACE_BEFORE_METHOD_CALL_PARENTHESES", "Method call parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_METHOD_PARENTHESES", "Method declaration parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_IF_PARENTHESES", "\"if\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_WHILE_PARENTHESES", "\"while\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_FOR_PARENTHESES", "\"for\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_CATCH_PARENTHESES", "\"catch\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SWITCH_PARENTHESES", "\"switch\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_PARENTHESES", "\"synchronized\" parentheses", BEFORE_PARENTHESES);
    initBooleanField("SPACE_BEFORE_ANOTATION_PARAMETER_LIST", "Annotation parameters", BEFORE_PARENTHESES);

    initBooleanField("SPACE_AROUND_ASSIGNMENT_OPERATORS", "Assignment operators (=, +=, ...)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_LOGICAL_OPERATORS", "Logical operators (&&, ||)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_EQUALITY_OPERATORS", "Equality operators (==, !=)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_RELATIONAL_OPERATORS", "Relational operators (<, >, <=, >=)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_BITWISE_OPERATORS", "Bitwise operators (&, |, ^)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_ADDITIVE_OPERATORS", "Additive operators (+, -)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_MULTIPLICATIVE_OPERATORS", "Multiplicative operators (*, /, %)", AROUND_OPERATORS);
    initBooleanField("SPACE_AROUND_SHIFT_OPERATORS", "Shift operators (<<, >>, >>>)", AROUND_OPERATORS);

    initBooleanField("SPACE_BEFORE_CLASS_LBRACE", "Class left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_METHOD_LBRACE", "Method left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_IF_LBRACE", "\"if\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ELSE_LBRACE", "\"else\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_WHILE_LBRACE", "\"while\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FOR_LBRACE", "\"for\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_DO_LBRACE", "\"do\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SWITCH_LBRACE", "\"switch\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_TRY_LBRACE", "\"try\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_CATCH_LBRACE", "\"catch\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_FINALLY_LBRACE", "\"finally\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_SYNCHRONIZED_LBRACE", "\"synchronized\" left brace", BEFORE_LEFT_BRACE);
    initBooleanField("SPACE_BEFORE_ARRAY_INITIALIZER_LBRACE", "Array initializer left brace", BEFORE_LEFT_BRACE);

    initBooleanField("SPACE_WITHIN_PARENTHESES", "Parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_METHOD_CALL_PARENTHESES", "Method call parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_METHOD_PARENTHESES", "Method declaration parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_IF_PARENTHESES", "\"if\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_WHILE_PARENTHESES", "\"while\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_FOR_PARENTHESES", "\"for\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_CATCH_PARENTHESES", "\"catch\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_SWITCH_PARENTHESES", "\"switch\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_SYNCHRONIZED_PARENTHESES", "\"synchronized\"  parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_CAST_PARENTHESES", "Type cast parentheses", WITHIN_PARENTHESES);
    initBooleanField("SPACE_WITHIN_ANNOTATION_PARENTHESES", "Annotation parentheses", WITHIN_PARENTHESES);

    initBooleanField("SPACE_BEFORE_QUEST", "Before '?'", TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_QUEST", "After '?'", TERNARY_OPERATOR);
    initBooleanField("SPACE_BEFORE_COLON", "Before ':'", TERNARY_OPERATOR);
    initBooleanField("SPACE_AFTER_COLON", "After ':'", TERNARY_OPERATOR);

    initBooleanField("SPACE_AFTER_LABEL", "After ':' in label declaration", OTHER);
    initBooleanField("SPACE_WITHIN_BRACKETS", "Within brackets", OTHER);
    initBooleanField("SPACE_WITHIN_ARRAY_INITIALIZER_BRACES", "Within array initializer braces", OTHER);
    initBooleanField("SPACE_AFTER_COMMA", "After comma", OTHER);
    initBooleanField("SPACE_BEFORE_COMMA", "Before comma", OTHER);
    initBooleanField("SPACE_AFTER_SEMICOLON", "After semicolon", OTHER);
    initBooleanField("SPACE_BEFORE_SEMICOLON", "Before semicolon", OTHER);
    initBooleanField("SPACE_AFTER_TYPE_CAST", "After type cast", OTHER);
  }

  protected void setupEditorSettings(Editor editor) {
    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
  }

  protected String getPreviewText() {
    return "@Annotation(param1=\"value1\", param2=\"value2\") public class Foo {\n" +
           "  int[] X = new int[]{1,3,5,6,7,87,1213,2};\n\n" +
           "  public void foo(int x, int y) {\n" +
           "    for(int i = 0; i < x; i++){\n" +
           "      y += (y ^ 0x123) << 2;\n" +
           "    }\n" +
           "    do {\n" +
           "      try {\n" +
           "        if(0 < x && x < 10) {\n" +
           "          while(x != y){\n" +
           "            x = f(x * 3 + 5);\n" +
           "          }\n" +
           "        } else {\n" +
           "          synchronized(this){\n" +
           "            switch(e.getCode()){\n" +
           "              //...\n" +
           "            }\n" +
           "          }\n" +
           "        }\n" +
           "      }\n" +
           "      catch(MyException e) {\n" +
           "      }\n" +
           "      finally {\n" +
           "        int[] arr = (int[])g(y);\n" +
           "        x = y >= 0 ? arr[y] : -1;\n" +
           "      }\n" +
           "    }while(true);\n" +
           "  }\n" +
           "}";
  }

  public JComponent getPanel() {
    return getInternalPanel();
  }
}