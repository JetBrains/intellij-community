package com.intellij.application.options;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;

/**
 * @author max
 */
public class WrappingPanel extends OptionTableWithPreviewPanel {
  private static final String METHOD_PARAMETERS_WRAPPING = "Method declaration parameters";
  private static final String CALL_PARAMETERS_WRAPPING = "Method call arguments";
  private static final String CALL_CHAIN_WRAPPING = "Chained method calls";
  private static final String FOR_STATEMENT_WRAPPING = "for(;;) statement";
  private static final String BINARY_OPERATION_WRAPPING = "Binary operations";
  private static final String[] FULL_WRAP_OPTIONS = new String[]{"Do not wrap", "Wrap if long",
                                                                 "Chop down if long", "Wrap always"};
  private static final String[] SINGLE_ITEM_WRAP_OPTIONS = new String[]{"Do not wrap", "Wrap if long",
                                                                        "Wrap always"};
  private static final int[] FULL_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                          CodeStyleSettings.WRAP_AS_NEEDED,
                                                          CodeStyleSettings.WRAP_AS_NEEDED |
                                                          CodeStyleSettings.WRAP_ON_EVERY_ITEM,
                                                          CodeStyleSettings.WRAP_ALWAYS};
  private static final int[] SINGLE_ITEM_WRAP_VALUES = new int[]{CodeStyleSettings.DO_NOT_WRAP,
                                                                 CodeStyleSettings.WRAP_AS_NEEDED,
                                                                 CodeStyleSettings.WRAP_ALWAYS};
  private static final String EXTENDS_LIST_WRAPPING = "Extends/implements list";
  private static final String EXTENDS_KEYWORD_WRAPPING = "Extends/implements keyword";
  private static final String THROWS_LIST_WRAPPING = "Throws list";
  private static final String THROWS_KEYWORD_WRAPPING = "Throws keyword";
  private static final String PARENTHESIZED_EXPRESSION = "Parenthesized expression";
  private static final String TERNARY_OPERATION_WRAPPING = "Ternary operation";
  private static final String ASSIGNMENT_WRAPPING = "Assignment statement";
  private static final String ARRAY_INITIALIZER_WRAPPING = "Array initializer";
  private static final String LABELED_STATEMENT_WRAPPING = "Label declaration";
  private static final String MODIFIER_LIST_WRAPPING = "Modifier list";

  public WrappingPanel(CodeStyleSettings settings) {
    super(settings);
  }

  protected void initTables() {
    initRadioGroupField("EXTENDS_LIST_WRAP", EXTENDS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("EXTENDS_KEYWORD_WRAP", EXTENDS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS,
                        SINGLE_ITEM_WRAP_VALUES);

    initRadioGroupField("METHOD_PARAMETERS_WRAP", METHOD_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE", "New line after '('", METHOD_PARAMETERS_WRAPPING);
    initBooleanField("METHOD_PARAMETERS_RPAREN_ON_NEXT_LINE", "Place ')' on new line", METHOD_PARAMETERS_WRAPPING);

    initRadioGroupField("THROWS_LIST_WRAP", THROWS_LIST_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initRadioGroupField("THROWS_KEYWORD_WRAP", THROWS_KEYWORD_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS,
                        SINGLE_ITEM_WRAP_VALUES);

    initRadioGroupField("CALL_PARAMETERS_WRAP", CALL_PARAMETERS_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("CALL_PARAMETERS_LPAREN_ON_NEXT_LINE", "New line after '('", CALL_PARAMETERS_WRAPPING);
    initBooleanField("CALL_PARAMETERS_RPAREN_ON_NEXT_LINE", "Place ')' on new line", CALL_PARAMETERS_WRAPPING);

    initRadioGroupField("METHOD_CALL_CHAIN_WRAP", CALL_CHAIN_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);

    initRadioGroupField("FOR_STATEMENT_WRAP", FOR_STATEMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("FOR_STATEMENT_LPAREN_ON_NEXT_LINE", "New line after '('", FOR_STATEMENT_WRAPPING);
    initBooleanField("FOR_STATEMENT_RPAREN_ON_NEXT_LINE", "Place ')' on new line", FOR_STATEMENT_WRAPPING);

    initRadioGroupField("BINARY_OPERATION_WRAP", BINARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("BINARY_OPERATION_SIGN_ON_NEXT_LINE", "Operation sign on next line", BINARY_OPERATION_WRAPPING);

    initRadioGroupField("ASSIGNMENT_WRAP", ASSIGNMENT_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("PLACE_ASSIGNMENT_SIGN_ON_NEXT_LINE", "Assignment sign on next line", ASSIGNMENT_WRAPPING);

    initRadioGroupField("TERNARY_OPERATION_WRAP", TERNARY_OPERATION_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("TERNARY_OPERATION_SIGNS_ON_NEXT_LINE", "'?' and ':' signs on next line",
                     TERNARY_OPERATION_WRAPPING);

    initBooleanField("PARENTHESES_EXPRESSION_LPAREN_WRAP", "New line after '('", PARENTHESIZED_EXPRESSION);
    initBooleanField("PARENTHESES_EXPRESSION_RPAREN_WRAP", "Place ')' on new line", PARENTHESIZED_EXPRESSION);

    initRadioGroupField("ARRAY_INITIALIZER_WRAP", ARRAY_INITIALIZER_WRAPPING, FULL_WRAP_OPTIONS, FULL_WRAP_VALUES);
    initBooleanField("ARRAY_INITIALIZER_LBRACE_ON_NEXT_LINE", "New line after '{'", ARRAY_INITIALIZER_WRAPPING);
    initBooleanField("ARRAY_INITIALIZER_RBRACE_ON_NEXT_LINE", "Place '}' on new line", ARRAY_INITIALIZER_WRAPPING);

    initRadioGroupField("LABELED_STATEMENT_WRAP", LABELED_STATEMENT_WRAPPING, SINGLE_ITEM_WRAP_OPTIONS, SINGLE_ITEM_WRAP_VALUES);

    initBooleanField("MODIFIER_LIST_WRAP", "Wrap after modifier list", MODIFIER_LIST_WRAPPING);
    initBooleanField("ANNOTATIONS_LIST_WRAP", "Wrap after annotations before modifier", MODIFIER_LIST_WRAPPING);
    initBooleanField("ANNOTATION_WRAP", "Wrap after each annotation", MODIFIER_LIST_WRAPPING);
  }

  protected void setupEditorSettings(Editor editor) {
    EditorSettings editorSettings = editor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setLineMarkerAreaShown(false);
    editorSettings.setLineNumbersShown(false);
    editorSettings.setFoldingOutlineShown(false);
    editorSettings.setAdditionalColumnsCount(0);
    editorSettings.setAdditionalLinesCount(1);
    editorSettings.setRightMargin(getRightMargin());
    editorSettings.setRightMarginShown(true);
  }

  protected int getRightMargin() {
    return 37;
  }

  protected String getPreviewText() {          //| Margin is here
    return "/*\n" +
           " * This is a sample file.\n" +
           " */\n" +
           "\n" +
           "@Annotation1 @Annotation2 public class ThisIsASampleClass extends C1 implements I1, I2, I3, I4, I5 {\n" +
           "  private int f1;\n" +
           "  private int f2;\n" +
           "  public void foo1(int i1, int i2, int i3, int i4, int i5, int i6, int i7) {}\n" +
           "  public static void longerMethod() throws Exception1, Exception2, Exception3 {\n" +
           "    int[] a = new int[] {1, 2, 0x0052, 0x0053, 0x0054};\n" +
           "    foo1(0x0051, 0x0052, 0x0053, 0x0054, 0x0055, 0x0056, 0x0057);\n" +
           "    int x = (3 + 4 + 5 + 6) * (7 + 8 + 9 + 10) * (11 + 12 + 13 + 14 + 0xFFFFFFFF);\n" +
           "    String s1, s2, s3;\n" +
           "    s1 = s2 = s3 = \"012345678901456\";\n" +
           "    int y = 2 > 3 ? 7 + 8 + 9 : 11 + 12 + 13;\n" +
           "    label: " +
           "    for (int i = 0; i < 0xFFFFFF; i += 2) {\n" +
           "       super.getFoo().foo().getBar().bar();\n" +
           "    }\n" +
           "  }\n" +
           "}";
  }
}
