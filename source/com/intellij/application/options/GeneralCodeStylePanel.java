package com.intellij.application.options;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

public class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  private static final String SYSTEM_DEPENDANT_STRING = ApplicationBundle.message("combobox.crlf.system.dependent");
  private static final String UNIX_STRING = ApplicationBundle.message("combobox.crlf.unix");
  private static final String WINDOWS_STRING = ApplicationBundle.message("combobox.crlf.windows");
  private static final String MACINTOSH_STRING = ApplicationBundle.message("combobox.crlf.mac");

  private JCheckBox myCbUseSameIndents;
  private IndentOptions myJavaIndentOptions = new IndentOptions(IndentOptions.LIST_LABEL_INDENT +
                                                                IndentOptions.LIST_CONT_INDENT
                                                                + IndentOptions.LIST_SMART_TABS);
  private IndentOptions myJspIndentOptions = new IndentOptions(IndentOptions.LIST_CONT_INDENT + IndentOptions.LIST_SMART_TABS);
  private IndentOptions myXMLIndentOptions = new IndentOptions(IndentOptions.LIST_CONT_INDENT + IndentOptions.LIST_SMART_TABS);
  private IndentOptions myOtherIndentOptions = new IndentOptions(0);
  private TabbedPaneWrapper myIndentOptionsTabs;
  private JCheckBox myCbDontIndentTopLevelMembers;
  private JPanel myIndentPanel;
  private JPanel myPreviewPanel;
  private JTextField myRightMarginField;
  private JComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private int myRightMargin;


  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);
    myIndentPanel.setLayout(new BorderLayout());
    myIndentPanel.add(createTabOptionsPanel(), BorderLayout.CENTER);
    installPreviewPanel(myPreviewPanel);
    myLineSeparatorCombo.addItem(SYSTEM_DEPENDANT_STRING);
    myLineSeparatorCombo.addItem(UNIX_STRING);
    myLineSeparatorCombo.addItem(WINDOWS_STRING);
    myLineSeparatorCombo.addItem(MACINTOSH_STRING);
    addPanelToWatch(myPanel);

    myRightMargin = settings.RIGHT_MARGIN;

    myRightMarginField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        int valueFromControl = getRightMarginImpl();
        if (valueFromControl > 0) {
          myRightMargin = valueFromControl;
        }
      }

      public void removeUpdate(DocumentEvent e) {
        int valueFromControl = getRightMarginImpl();
        if (valueFromControl > 0) {
          myRightMargin = valueFromControl;
        }
      }

      public void changedUpdate(DocumentEvent e) {
        int valueFromControl = getRightMarginImpl();
        if (valueFromControl > 0) {
          myRightMargin = valueFromControl;
        }
      }
    });
  }

  protected void somethingChanged() {
    update();
  }

  private void update() {
    myJavaIndentOptions.setEnabled(true);
    boolean enabled = !myCbUseSameIndents.isSelected();
    if (!enabled && myIndentOptionsTabs.getSelectedIndex() != 0) {
      myIndentOptionsTabs.setSelectedIndex(0);
    }
    myJspIndentOptions.setEnabled(enabled);
    myIndentOptionsTabs.setEnabledAt(1, enabled);
    myXMLIndentOptions.setEnabled(enabled);
    myIndentOptionsTabs.setEnabledAt(2, enabled);
    myOtherIndentOptions.setEnabled(enabled);
    myIndentOptionsTabs.setEnabledAt(3, enabled);
  }

  private JPanel createTabOptionsPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("group.tabs.and.indents"));

    myCbUseSameIndents = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.same.settings.for.all.file.types"));
    optionGroup.add(myCbUseSameIndents);

    myIndentOptionsTabs = new TabbedPaneWrapper(JTabbedPane.RIGHT);
    myIndentOptionsTabs.addTab(ApplicationBundle.message("tab.indent.java"), myJavaIndentOptions.createPanel());
    myIndentOptionsTabs.addTab(ApplicationBundle.message("tab.indent.jsp"), myJspIndentOptions.createPanel());
    myIndentOptionsTabs.addTab(ApplicationBundle.message("tab.indent.xml"), myXMLIndentOptions.createPanel());
    myIndentOptionsTabs.addTab(ApplicationBundle.message("tab.indent.other"), myOtherIndentOptions.createPanel());
    optionGroup.add(myIndentOptionsTabs.getComponent());

    return optionGroup.createPanel();
  }


  protected int getRightMargin() {
    return myRightMargin;
  }

  @NotNull
  protected FileType getFileType() {
    return StdFileTypes.JAVA;
  }

  protected String getPreviewText() {
    return "public class Foo {\n" +
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
           "      }\n" +
           "      catch(Exception e) {\n" +
           "        processException(e.getMessage(),\n" +
           "          x + y, z, a);\n" +
           "      }\n" +
           "      finally {\n" +
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

  public void apply(CodeStyleSettings settings) {
    settings.LINE_SEPARATOR = getSelectedLineSeparator();
    settings.USE_SAME_INDENTS = myCbUseSameIndents.isSelected();
    myJavaIndentOptions.apply(settings.JAVA_INDENT_OPTIONS);
    myJspIndentOptions.apply(settings.JSP_INDENT_OPTIONS);
    myXMLIndentOptions.apply(settings.XML_INDENT_OPTIONS);
    myOtherIndentOptions.apply(settings.OTHER_INDENT_OPTIONS);

    settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS = myCbDontIndentTopLevelMembers.isSelected();

    int rightMarginImpl = getRightMarginImpl();
    if (rightMarginImpl > 0) {
      settings.RIGHT_MARGIN = rightMarginImpl;
    }

  }

  private int getRightMarginImpl() {
    if (myRightMarginField == null) return -1;
    try {
      return Integer.parseInt(myRightMarginField.getText());
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  private String getSelectedLineSeparator() {
    if (UNIX_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\n";
    }
    else if (MACINTOSH_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r";
    }
    else if (WINDOWS_STRING.equals(myLineSeparatorCombo.getSelectedItem())) {
      return "\r\n";
    }
    return null;
  }


  public boolean isModified(CodeStyleSettings settings) {
    if (!Comparing.equal(getSelectedLineSeparator(), settings.LINE_SEPARATOR)) {
      return true;
    }
    if (myCbUseSameIndents.isSelected() != settings.USE_SAME_INDENTS) {
      return true;
    }
    if (myJavaIndentOptions.isModified(settings.JAVA_INDENT_OPTIONS)) {
      return true;
    }
    if (myJspIndentOptions.isModified(settings.JSP_INDENT_OPTIONS)) {
      return true;
    }
    if (myXMLIndentOptions.isModified(settings.XML_INDENT_OPTIONS)) {
      return true;
    }
    if (myOtherIndentOptions.isModified(settings.OTHER_INDENT_OPTIONS)) {
      return true;
    }

    if (myCbDontIndentTopLevelMembers.isSelected() != settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS) {
      return true;
    }
    if (!myRightMarginField.getText().equals(String.valueOf(settings.RIGHT_MARGIN))) {
      return true;
    }

    return false;
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void resetImpl(final CodeStyleSettings settings) {
    myCbUseSameIndents.setSelected(settings.USE_SAME_INDENTS);

    myJavaIndentOptions.reset(settings.JAVA_INDENT_OPTIONS);
    myJspIndentOptions.reset(settings.JSP_INDENT_OPTIONS);
    myXMLIndentOptions.reset(settings.XML_INDENT_OPTIONS);
    myOtherIndentOptions.reset(settings.OTHER_INDENT_OPTIONS);

    myCbDontIndentTopLevelMembers.setSelected(settings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS);

    String lineSeparator = settings.LINE_SEPARATOR;
    if ("\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(UNIX_STRING);
    }
    else if ("\r\n".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(WINDOWS_STRING);
    }
    else if ("\r".equals(lineSeparator)) {
      myLineSeparatorCombo.setSelectedItem(MACINTOSH_STRING);
    }
    else {
      myLineSeparatorCombo.setSelectedItem(SYSTEM_DEPENDANT_STRING);
    }

    myRightMarginField.setText(String.valueOf(settings.RIGHT_MARGIN));
    update();
  }

  protected LexerEditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return HighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST);
  }

  private class IndentOptions extends OptionGroup {
    public static final int LIST_CONT_INDENT = 1;
    public static final int LIST_LABEL_INDENT = 2;
    public static final int LIST_SMART_TABS = 4;
    private final int myListFlags;

    private boolean isListSmartTabs() {
      return (myListFlags & LIST_SMART_TABS) != 0;
    }

    private boolean isListContIndent() {
      return (myListFlags & LIST_CONT_INDENT) != 0;
    }

    private boolean isListLabelIndent() {
      return (myListFlags & LIST_LABEL_INDENT) != 0;
    }

    private JTextField myIndentField;
    private JTextField myContinuationIndentField;
    private JCheckBox myCbUseTab;
    private JCheckBox myCbSmartTabs;
    private JTextField myTabSizeField;
    private JLabel myTabSizeLabel;
    private JLabel myIndentLabel;
    private JLabel myContinuationIndentLabel;

    private JTextField myLabelIndent;
    private JLabel myLabelIndentLabel;

    private JCheckBox myLabelIndentAbsolute;

    public IndentOptions(int listFlags) {
      myListFlags = listFlags;
    }

    public JPanel createPanel() {
      myCbUseTab = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.tab.character"));
      add(myCbUseTab);

      if (isListSmartTabs()) {
        myCbSmartTabs = new JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"));
        add(myCbSmartTabs, true);
      }

      myTabSizeField = new JTextField(4);
      myTabSizeField.setMinimumSize(myTabSizeField.getPreferredSize());
      myTabSizeLabel = new JLabel(ApplicationBundle.message("editbox.indent.tab.size"));
      add(myTabSizeLabel, myTabSizeField);

      myIndentField = new JTextField(4);
      myIndentField.setMinimumSize(myTabSizeField.getPreferredSize());
      myIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.indent"));
      add(myIndentLabel, myIndentField);

      if (isListContIndent()) {
        myContinuationIndentField = new JTextField(4);
        myContinuationIndentField.setMinimumSize(myContinuationIndentField.getPreferredSize());
        myContinuationIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.continuation.indent"));
        add(myContinuationIndentLabel, myContinuationIndentField);
      }

      if (isListLabelIndent()) {
        myLabelIndent = new JTextField(4);
        add(myLabelIndentLabel = new JLabel(ApplicationBundle.message("editbox.indent.label.indent")), myLabelIndent);

        myLabelIndentAbsolute = new JCheckBox(ApplicationBundle.message("checkbox.indent.absolute.label.indent"));
        add(myLabelIndentAbsolute, true);

        myCbDontIndentTopLevelMembers = new JCheckBox(ApplicationBundle.message("checkbox.do.not.indent.top.level.class.members"));
        add(myCbDontIndentTopLevelMembers);
      }

      final JPanel result = super.createPanel();
      result.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
      return result;
    }

    private boolean isModified(JCheckBox checkBox, boolean value) {
      return checkBox.isSelected() != value;
    }

    private boolean isModified(JTextField textField, int value) {
      try {
        int fieldValue = Integer.parseInt(textField.getText().trim());
        return fieldValue != value;
      }
      catch (NumberFormatException e) {
        return false;
      }
    }

    public boolean isModified(CodeStyleSettings.IndentOptions options) {
      boolean isModified;
      isModified = isModified(myTabSizeField, options.TAB_SIZE);
      isModified |= isModified(myCbUseTab, options.USE_TAB_CHARACTER);
      isModified |= isModified(myIndentField, options.INDENT_SIZE);

      if (isListSmartTabs()) {
        isModified |= isModified(myCbSmartTabs, options.SMART_TABS);
      }

      if (isListContIndent()) {
        isModified |= isModified(myContinuationIndentField, options.CONTINUATION_INDENT_SIZE);
      }

      if (isListLabelIndent()) {
        isModified |= isModified(myLabelIndent, options.LABEL_INDENT_SIZE);
        isModified |= isModified(myLabelIndentAbsolute, options.LABEL_INDENT_ABSOLUTE);
      }
      return isModified;
    }

    private int getUIIndent() {
      try {
        return Math.max(Integer.parseInt(myIndentField.getText()), 1);
      }
      catch (NumberFormatException e) {
        //stay with default
      }

      return 4;
    }

    private int getUITabSize() {
      try {
        return Math.max(Integer.parseInt(myTabSizeField.getText()), 1);
      }
      catch (NumberFormatException e) {
        //stay with default
      }

      return 4;
    }

    public void apply(CodeStyleSettings.IndentOptions options) {
      options.INDENT_SIZE = getUIIndent();
      options.TAB_SIZE = getUITabSize();
      options.USE_TAB_CHARACTER = myCbUseTab.isSelected();

      if (isListContIndent()) {
        try {
          options.CONTINUATION_INDENT_SIZE = Math.max(Integer.parseInt(myContinuationIndentField.getText()), 0);
        }
        catch (NumberFormatException e) {
          //stay with default
        }
      }

      if (isListSmartTabs()) {
        options.SMART_TABS = isSmartTabValid(options.INDENT_SIZE, options.TAB_SIZE) && myCbSmartTabs.isSelected();
      }

      if (isListLabelIndent()) {
        try {
          options.LABEL_INDENT_SIZE = Integer.parseInt(myLabelIndent.getText());
        }
        catch (NumberFormatException e) {
          //stay with default
        }
        options.LABEL_INDENT_ABSOLUTE = myLabelIndentAbsolute.isSelected();
      }
    }

    public void reset(CodeStyleSettings.IndentOptions options) {
      myTabSizeField.setText(String.valueOf(options.TAB_SIZE));
      myCbUseTab.setSelected(options.USE_TAB_CHARACTER);

      myIndentField.setText(String.valueOf(options.INDENT_SIZE));
      if (isListContIndent()) myContinuationIndentField.setText(String.valueOf(options.CONTINUATION_INDENT_SIZE));
      if (isListLabelIndent()) {
        myLabelIndent.setText(Integer.toString(options.LABEL_INDENT_SIZE));
        myLabelIndentAbsolute.setSelected(options.LABEL_INDENT_ABSOLUTE);
      }
      if (isListSmartTabs()) myCbSmartTabs.setSelected(options.SMART_TABS);
    }

    public void setEnabled(boolean enabled) {
      myIndentField.setEnabled(enabled);
      myIndentLabel.setEnabled(enabled);
      myTabSizeField.setEnabled(enabled);
      myTabSizeLabel.setEnabled(enabled);
      myCbUseTab.setEnabled(enabled);
      if (isListSmartTabs()) {
        boolean smartTabsChecked = enabled && myCbUseTab.isSelected();
        boolean smartTabsValid = smartTabsChecked && isSmartTabValid(getUIIndent(), getUITabSize());
        myCbSmartTabs.setEnabled(smartTabsValid);
        myCbSmartTabs.setToolTipText(
          smartTabsChecked && !smartTabsValid ? ApplicationBundle.message("tooltip.indent.must.be.multiple.of.tab.size.for.smart.tabs.to.operate") : null);
      }
      if (isListLabelIndent()) {
        myContinuationIndentField.setEnabled(enabled);
        myContinuationIndentLabel.setEnabled(enabled);
      }
      if (isListLabelIndent()) {
        myLabelIndent.setEnabled(enabled);
        myLabelIndentLabel.setEnabled(enabled);
        myLabelIndentAbsolute.setEnabled(enabled);
      }
    }

    private boolean isSmartTabValid(int indent, int tabSize) {
      return (indent / tabSize) * tabSize == indent;
    }
  }

}
