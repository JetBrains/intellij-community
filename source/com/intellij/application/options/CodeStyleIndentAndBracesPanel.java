package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

public class CodeStyleIndentAndBracesPanel extends CodeStyleAbstractPanel {
  private static final String[] BRACE_PLACEMENT_OPTIONS = new String[]{
    "End of line",
    "Next line if wrapped",
    "Next line",
    "Next line shifted",
    "Next line shifted2"
  };

  private static final int[] BRACE_PLACEMENT_VALUES = new int[] {
    CodeStyleSettings.END_OF_LINE,
    CodeStyleSettings.NEXT_LINE_IF_WRAPPED,
    CodeStyleSettings.NEXT_LINE,
    CodeStyleSettings.NEXT_LINE_SHIFTED,
    CodeStyleSettings.NEXT_LINE_SHIFTED2
  };

  private static final String[] BRACE_FORCE_OPTIONS = new String[]{
    "Do not force",
    "When multiline",
    "Always"
  };

  private static final int[] BRACE_FORCE_VALUES = new int[]{
    CodeStyleSettings.DO_NOT_FORCE,
    CodeStyleSettings.FORCE_BRACES_IF_MULTILINE,
    CodeStyleSettings.FORCE_BRACES_ALWAYS
  };

  private JComboBox myClassDeclarationCombo = new JComboBox();
  private JComboBox myMethodDeclarationCombo = new JComboBox();
  private JComboBox myOtherCombo = new JComboBox();

  private JCheckBox myCbElseOnNewline;
  private JCheckBox myCbWhileOnNewline;
  private JCheckBox myCbCatchOnNewline;
  private JCheckBox myCbFinallyOnNewline;

  private JCheckBox myCbSpecialElseIfTreatment;
  private JCheckBox myCbIndentCaseFromSwitch;


  private JComboBox myIfForceCombo;
  private JComboBox myForForceCombo;
  private JComboBox myWhileForceCombo;
  private JComboBox myDoWhileForceCombo;

  private JCheckBox myAlignDeclarationParameters;
  private JCheckBox myAlignCallParameters;
  private JCheckBox myAlignExtendsList;
  private JCheckBox myAlignForStatement;
  private JCheckBox myAlignThrowsList;
  private JCheckBox myAlignParenthesizedExpression;
  private JCheckBox myAlignBinaryExpression;
  private JCheckBox myAlignTernaryExpression;
  private JCheckBox myAlignAssignment;
  private JCheckBox myAlignArrayInitializerExpression;
  private JCheckBox myKeepLineBreaks;
  private JCheckBox myKeepCommentAtFirstColumn;
  private JCheckBox myKeepMethodsInOneLine;
  private JCheckBox myKeepSimpleBlocksInOneLine;
  private JCheckBox myKeepControlStatementInOneLine;

  private final JPanel myPanel = new JPanel(new GridBagLayout());

  public CodeStyleIndentAndBracesPanel(CodeStyleSettings settings) {
    super(settings);

    myPanel.add(createKeepWhenReformatingPanel(),
        new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createBracesPanel(),
        new GridBagConstraints(0, 1, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createAlignmentsPanel(),
        new GridBagConstraints(1, 0, 1, 2, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                               new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(createPlaceOnNewLinePanel(),
        new GridBagConstraints(1, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.HORIZONTAL,
                               new Insets(0, 4, 0, 4), 0, 0));
    myPanel.add(createForceBracesPanel(),
        new GridBagConstraints(0, 2, 1, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                               new Insets(0, 4, 0, 4), 0, 0));

    myPanel.add(new JPanel() {
      public Dimension getPreferredSize() {
        return new Dimension(1, 1);
      }
    }, new GridBagConstraints(0, 3, 2, 1, 0, 0, GridBagConstraints.NORTH, GridBagConstraints.NONE,
                              new Insets(0, 0, 0, 0), 0, 0));

    final JPanel previewPanel = createPreviewPanel();
    myPanel.add(previewPanel,
                new GridBagConstraints(2, 0, 1, 4, 1, 1, GridBagConstraints.NORTH, GridBagConstraints.BOTH,
                                       new Insets(0, 0, 0, 4), 0, 0));
    installPreviewPanel(previewPanel);
    addPanelToWatch(myPanel);
  }

  private Component createKeepWhenReformatingPanel() {
    OptionGroup optionGroup = new OptionGroup("Keep When Reformatting");

    myKeepLineBreaks = createCheckBox("Line breaks");
    optionGroup.add(myKeepLineBreaks);

    myKeepCommentAtFirstColumn = createCheckBox("Comment at first column");
    optionGroup.add(myKeepCommentAtFirstColumn);

    myKeepMethodsInOneLine = createCheckBox("Simple methods in one line");
    optionGroup.add(myKeepMethodsInOneLine);

    myKeepSimpleBlocksInOneLine = createCheckBox("Simple blocks in one line");
    optionGroup.add(myKeepSimpleBlocksInOneLine);

    myKeepControlStatementInOneLine = createCheckBox("Control statement in one line");
    optionGroup.add(myKeepControlStatementInOneLine);


    return optionGroup.createPanel();

  }

  private JPanel createBracesPanel() {
    OptionGroup optionGroup = new OptionGroup("Braces Placement");

    myClassDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Class declaration:"), myClassDeclarationCombo);

    myMethodDeclarationCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Method declaration:"), myMethodDeclarationCombo);

    myOtherCombo = createBraceStyleCombo();
    optionGroup.add(new JLabel("Other:"), myOtherCombo);

    myCbSpecialElseIfTreatment = createCheckBox("Special \"else if\" treatment");
    optionGroup.add(myCbSpecialElseIfTreatment);

    myCbIndentCaseFromSwitch = createCheckBox("Indent \"case\" from \"switch\"");
    optionGroup.add(myCbIndentCaseFromSwitch);

    return optionGroup.createPanel();
  }

  private JPanel createForceBracesPanel() {
    OptionGroup optionGroup = new OptionGroup("Force Braces");

    myIfForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("if ():"), myIfForceCombo);

    myForForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("for ():"), myForForceCombo);

    myWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("while ():"), myWhileForceCombo);

    myDoWhileForceCombo = createForceBracesCombo();
    optionGroup.add(new JLabel("do ... while():"), myDoWhileForceCombo);

    return optionGroup.createPanel();
  }

  private JPanel createAlignmentsPanel() {
    OptionGroup optionGroup = new OptionGroup("Align when multiline");

    myAlignDeclarationParameters = createCheckBox("Method parameters");
    optionGroup.add(myAlignDeclarationParameters);

    myAlignCallParameters = createCheckBox("Call arguments");
    optionGroup.add(myAlignCallParameters);

    myAlignExtendsList = createCheckBox("Extends list");
    optionGroup.add(myAlignExtendsList);

    myAlignThrowsList = createCheckBox("Throws list");
    optionGroup.add(myAlignThrowsList);

    myAlignParenthesizedExpression = createCheckBox("Parenthesized expression");
    optionGroup.add(myAlignParenthesizedExpression);

    myAlignBinaryExpression = createCheckBox("Binary operation");
    optionGroup.add(myAlignBinaryExpression);

    myAlignTernaryExpression = createCheckBox("Ternary operation");
    optionGroup.add(myAlignTernaryExpression);

    myAlignAssignment = createCheckBox("Assignments");
    optionGroup.add(myAlignAssignment);

    myAlignForStatement = createCheckBox("For statement");
    optionGroup.add(myAlignForStatement);

    myAlignArrayInitializerExpression = createCheckBox("Array initializer");
    optionGroup.add(myAlignArrayInitializerExpression);

    return optionGroup.createPanel();
  }

  private JPanel createPlaceOnNewLinePanel() {
    OptionGroup optionGroup = new OptionGroup("Place on New Line");

    myCbElseOnNewline = createCheckBox("\"else\" on new line");
    optionGroup.add(myCbElseOnNewline);

    myCbWhileOnNewline = createCheckBox("\"while\" on new line");
    optionGroup.add(myCbWhileOnNewline);

    myCbCatchOnNewline = createCheckBox("\"catch\" on new line");
    optionGroup.add(myCbCatchOnNewline);

    myCbFinallyOnNewline = createCheckBox("\"finally\" on new line");
    optionGroup.add(myCbFinallyOnNewline);

    return optionGroup.createPanel();
  }

  private JCheckBox createCheckBox(String text) {
    return new JCheckBox(text);
  }

  private JComboBox createForceBracesCombo() {
    return new JComboBox(BRACE_FORCE_OPTIONS);
  }

  private static void setForceBracesComboValue(JComboBox comboBox, int value) {
    for (int i = 0; i < BRACE_FORCE_VALUES.length; i++) {
      int forceValue = BRACE_FORCE_VALUES[i];
      if (forceValue == value) {
        comboBox.setSelectedItem(BRACE_FORCE_OPTIONS[i]);
      }
    }
  }

  private static int getForceBracesValue(JComboBox comboBox) {
    String selected = (String)comboBox.getSelectedItem();
    for (int i = 0; i < BRACE_FORCE_OPTIONS.length; i++) {
      String s = BRACE_FORCE_OPTIONS[i];
      if (s.equals(selected)) {
        return BRACE_FORCE_VALUES[i];
      }
    }
    return 0;
  }

  private JComboBox createBraceStyleCombo() {
    return new JComboBox(BRACE_PLACEMENT_OPTIONS);
  }

  private static void setBraceStyleComboValue(JComboBox comboBox, int value) {
    for (int i = 0; i < BRACE_PLACEMENT_OPTIONS.length; i++) {
      if (BRACE_PLACEMENT_VALUES[i] == value) {
        comboBox.setSelectedItem(BRACE_PLACEMENT_OPTIONS[i]);
        return;
      }
    }
  }

  private static int getBraceComboValue(JComboBox comboBox) {
    Object item = comboBox.getSelectedItem();
    for (int i = 1; i < BRACE_PLACEMENT_OPTIONS.length; i++) {
      if (BRACE_PLACEMENT_OPTIONS[i].equals(item)) {
        return BRACE_PLACEMENT_VALUES[i];
      }
    }
    return BRACE_PLACEMENT_VALUES[0];
  }

  private JPanel createPreviewPanel() {
    JPanel p = new JPanel(new BorderLayout());
    p.setBorder(IdeBorderFactory.createTitledBorder("Preview"));
    return p;
  }

  protected String getPreviewText() {
    return
      "public class Foo {\n" +
      "  public int[] X = new int[] { 1, 3, 5\n" +
      "  7, 9, 11};\n" +
      "  public void foo(boolean a, int x,\n" +
      "    int y, int z) {\n" +
      "    label1: do {\n" +
      "      try {\n" +
      "        if(x > 0) {\n" +
      "          int someVariable = a ? \n" +
      "             x : \n" +
      "             y;\n" +
      "        } else if (x < 0) {\n" +
      "          int someVariable = (y +\n" +
      "          z\n" +
      "          );\n" +
      "          someVariable = x = \n" +
      "          x +\n" +
      "          y;\n" +
      "        } else {\n" +
      "          label2:\n" +
      "          for (int i = 0;\n" +
      "               i < 5;\n" +
      "               i++) doSomething(i);\n" +
      "        }\n" +
      "        switch(a) {\n" +
      "          case 0: \n" +
      "           doCase0();\n" +
      "           break;\n" +
      "          default: \n" +
      "           doDefault();\n" +
      "        }\n" +
      "      }catch(Exception e) {\n" +
      "        processException(e.getMessage(),\n" +
      "          x + y, z, a);\n" +
      "      }finally {\n" +
      "        processFinally();\n" +
      "      }\n" +
      "    }while(true);\n" +
      "\n" +
      "    if (2 < 3) return;\n" +
      "    if (3 < 4)\n" +
      "       return;\n" +
      "    do x++ while (x < 10000);\n" +
      "    while (x < 50000) x++;\n" +
      "    for (int i = 0; i < 5; i++) System.out.println(i);\n" +
      "  }\n" +
      "  private class InnerClass implements I1,\n" +
      "  I2 {\n" +
      "    public void bar() throws E1,\n" +
      "     E2 {\n" +
      "    }\n" +
      "  }\n" +
      "}";
  }

  public boolean isModified(CodeStyleSettings settings) {
    boolean isModified;
    isModified = isModified(myCbElseOnNewline, settings.ELSE_ON_NEW_LINE);
    isModified |= isModified(myCbWhileOnNewline, settings.WHILE_ON_NEW_LINE);
    isModified |= isModified(myCbCatchOnNewline, settings.CATCH_ON_NEW_LINE);
    isModified |= isModified(myCbFinallyOnNewline, settings.FINALLY_ON_NEW_LINE);


    isModified |= isModified(myCbSpecialElseIfTreatment, settings.SPECIAL_ELSE_IF_TREATMENT);
    isModified |= isModified(myCbIndentCaseFromSwitch, settings.INDENT_CASE_FROM_SWITCH);


    isModified |= settings.BRACE_STYLE != getBraceComboValue(myOtherCombo);
    isModified |= settings.CLASS_BRACE_STYLE != getBraceComboValue(myClassDeclarationCombo);
    isModified |= settings.METHOD_BRACE_STYLE != getBraceComboValue(myMethodDeclarationCombo);

    isModified |= isModified(myAlignAssignment, settings.ALIGN_MULTILINE_ASSIGNMENT);
    isModified |= isModified(myAlignBinaryExpression, settings.ALIGN_MULTILINE_BINARY_OPERATION);
    isModified |= isModified(myAlignCallParameters, settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    isModified |= isModified(myAlignDeclarationParameters, settings.ALIGN_MULTILINE_PARAMETERS);
    isModified |= isModified(myAlignExtendsList, settings.ALIGN_MULTILINE_EXTENDS_LIST);
    isModified |= isModified(myAlignForStatement, settings.ALIGN_MULTILINE_FOR);
    isModified |= isModified(myAlignParenthesizedExpression, settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    isModified |= isModified(myAlignTernaryExpression, settings.ALIGN_MULTILINE_TERNARY_OPERATION);
    isModified |= isModified(myAlignThrowsList, settings.ALIGN_MULTILINE_THROWS_LIST);
    isModified |= isModified(myAlignArrayInitializerExpression, settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    isModified |= settings.FOR_BRACE_FORCE != getForceBracesValue(myForForceCombo);
    isModified |= settings.IF_BRACE_FORCE != getForceBracesValue(myIfForceCombo);
    isModified |= settings.WHILE_BRACE_FORCE != getForceBracesValue(myWhileForceCombo);
    isModified |= settings.DOWHILE_BRACE_FORCE != getForceBracesValue(myDoWhileForceCombo);

    isModified |= isModified(myKeepLineBreaks, settings.KEEP_LINE_BREAKS);
    isModified |= isModified(myKeepCommentAtFirstColumn, settings.KEEP_FIRST_COLUMN_COMMENT);
    isModified |= isModified(myKeepControlStatementInOneLine, settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    isModified |= isModified(myKeepSimpleBlocksInOneLine, settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    isModified |= isModified(myKeepMethodsInOneLine, settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);


    return isModified;

  }

  private static boolean isModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myCbElseOnNewline.setSelected(settings.ELSE_ON_NEW_LINE);
    myCbWhileOnNewline.setSelected(settings.WHILE_ON_NEW_LINE);
    myCbCatchOnNewline.setSelected(settings.CATCH_ON_NEW_LINE);
    myCbFinallyOnNewline.setSelected(settings.FINALLY_ON_NEW_LINE);

    myCbSpecialElseIfTreatment.setSelected(settings.SPECIAL_ELSE_IF_TREATMENT);
    myCbIndentCaseFromSwitch.setSelected(settings.INDENT_CASE_FROM_SWITCH);

    setBraceStyleComboValue(myOtherCombo, settings.BRACE_STYLE);
    setBraceStyleComboValue(myClassDeclarationCombo, settings.CLASS_BRACE_STYLE);
    setBraceStyleComboValue(myMethodDeclarationCombo, settings.METHOD_BRACE_STYLE);

    myAlignAssignment.setSelected(settings.ALIGN_MULTILINE_ASSIGNMENT);
    myAlignBinaryExpression.setSelected(settings.ALIGN_MULTILINE_BINARY_OPERATION);
    myAlignCallParameters.setSelected(settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS);
    myAlignDeclarationParameters.setSelected(settings.ALIGN_MULTILINE_PARAMETERS);
    myAlignExtendsList.setSelected(settings.ALIGN_MULTILINE_EXTENDS_LIST);
    myAlignForStatement.setSelected(settings.ALIGN_MULTILINE_FOR);
    myAlignParenthesizedExpression.setSelected(settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION);
    myAlignTernaryExpression.setSelected(settings.ALIGN_MULTILINE_TERNARY_OPERATION);
    myAlignThrowsList.setSelected(settings.ALIGN_MULTILINE_THROWS_LIST);
    myAlignArrayInitializerExpression.setSelected(settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION);

    setForceBracesComboValue(myForForceCombo, settings.FOR_BRACE_FORCE);
    setForceBracesComboValue(myIfForceCombo, settings.IF_BRACE_FORCE);
    setForceBracesComboValue(myWhileForceCombo, settings.WHILE_BRACE_FORCE);
    setForceBracesComboValue(myDoWhileForceCombo, settings.DOWHILE_BRACE_FORCE);

    myKeepLineBreaks.setSelected(settings.KEEP_LINE_BREAKS);
    myKeepCommentAtFirstColumn.setSelected(settings.KEEP_FIRST_COLUMN_COMMENT);
    myKeepControlStatementInOneLine.setSelected(settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE);
    myKeepSimpleBlocksInOneLine.setSelected(settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);
    myKeepMethodsInOneLine.setSelected(settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE);

  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST);
  }

  public void apply(CodeStyleSettings settings) {
    settings.ELSE_ON_NEW_LINE = myCbElseOnNewline.isSelected();
    settings.WHILE_ON_NEW_LINE = myCbWhileOnNewline.isSelected();
    settings.CATCH_ON_NEW_LINE = myCbCatchOnNewline.isSelected();
    settings.FINALLY_ON_NEW_LINE = myCbFinallyOnNewline.isSelected();


    settings.SPECIAL_ELSE_IF_TREATMENT = myCbSpecialElseIfTreatment.isSelected();
    settings.INDENT_CASE_FROM_SWITCH = myCbIndentCaseFromSwitch.isSelected();

    settings.BRACE_STYLE = getBraceComboValue(myOtherCombo);
    settings.CLASS_BRACE_STYLE = getBraceComboValue(myClassDeclarationCombo);
    settings.METHOD_BRACE_STYLE = getBraceComboValue(myMethodDeclarationCombo);

    settings.ALIGN_MULTILINE_ASSIGNMENT = myAlignAssignment.isSelected();
    settings.ALIGN_MULTILINE_BINARY_OPERATION = myAlignBinaryExpression.isSelected();
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignCallParameters.isSelected();
    settings.ALIGN_MULTILINE_PARAMETERS = myAlignDeclarationParameters.isSelected();
    settings.ALIGN_MULTILINE_EXTENDS_LIST = myAlignExtendsList.isSelected();
    settings.ALIGN_MULTILINE_FOR = myAlignForStatement.isSelected();
    settings.ALIGN_MULTILINE_PARENTHESIZED_EXPRESSION = myAlignParenthesizedExpression.isSelected();
    settings.ALIGN_MULTILINE_TERNARY_OPERATION = myAlignTernaryExpression.isSelected();
    settings.ALIGN_MULTILINE_THROWS_LIST = myAlignThrowsList.isSelected();
    settings.ALIGN_MULTILINE_ARRAY_INITIALIZER_EXPRESSION = myAlignArrayInitializerExpression.isSelected();
//    mySettings.LABEL_INDENT =

    settings.FOR_BRACE_FORCE = getForceBracesValue(myForForceCombo);
    settings.IF_BRACE_FORCE = getForceBracesValue(myIfForceCombo);
    settings.WHILE_BRACE_FORCE = getForceBracesValue(myWhileForceCombo);
    settings.DOWHILE_BRACE_FORCE = getForceBracesValue(myDoWhileForceCombo);

    settings.KEEP_LINE_BREAKS = myKeepLineBreaks.isSelected();
    settings.KEEP_FIRST_COLUMN_COMMENT = myKeepCommentAtFirstColumn.isSelected();
    settings.KEEP_CONTROL_STATEMENT_IN_ONE_LINE = myKeepControlStatementInOneLine.isSelected();
    settings.KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = myKeepSimpleBlocksInOneLine.isSelected();
    settings.KEEP_SIMPLE_METHODS_IN_ONE_LINE = myKeepMethodsInOneLine.isSelected();

  }

  protected FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  protected int getRightMargin() {
    return -1;
  }

  public JComponent getPanel() {
    return myPanel;
  }
}