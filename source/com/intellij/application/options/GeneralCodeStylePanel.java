package com.intellij.application.options;

import com.intellij.ide.highlighter.JavaHighlighterFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.FileTypeIndentOptionsProvider;
import com.intellij.ui.OptionGroup;
import com.intellij.ui.TabbedPaneWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeneralCodeStylePanel extends CodeStyleAbstractPanel {
  private static final String SYSTEM_DEPENDANT_STRING = ApplicationBundle.message("combobox.crlf.system.dependent");
  private static final String UNIX_STRING = ApplicationBundle.message("combobox.crlf.unix");
  private static final String WINDOWS_STRING = ApplicationBundle.message("combobox.crlf.windows");
  private static final String MACINTOSH_STRING = ApplicationBundle.message("combobox.crlf.mac");

  private JCheckBox myCbUseSameIndents;
  private IndentOptionsEditor myOtherIndentOptions = new IndentOptionsEditor();

  private Map<FileType, IndentOptionsEditor> myAdditionalIndentOptions = new LinkedHashMap<FileType, IndentOptionsEditor>();

  private TabbedPaneWrapper myIndentOptionsTabs;
  private JPanel myIndentPanel;
  private JPanel myPreviewPanel;
  private JTextField myRightMarginField;
  private JComboBox myLineSeparatorCombo;
  private JPanel myPanel;
  private int myRightMargin;


  public GeneralCodeStylePanel(CodeStyleSettings settings) {
    super(settings);

    final FileTypeIndentOptionsProvider[] indentOptionsProviders = Extensions.getExtensions(FileTypeIndentOptionsProvider.EP_NAME);
    for (FileTypeIndentOptionsProvider indentOptionsProvider : indentOptionsProviders) {
      myAdditionalIndentOptions.put(indentOptionsProvider.getFileType(), indentOptionsProvider.createOptionsEditor());
    }

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
    boolean enabled = !myCbUseSameIndents.isSelected();
    if (!enabled && myIndentOptionsTabs.getSelectedIndex() != 0) {
      myIndentOptionsTabs.setSelectedIndex(0);
    }

    int index = 0;
    for(IndentOptionsEditor options:myAdditionalIndentOptions.values()) {
      if (index > 0) {
        options.setEnabled(enabled);
        myIndentOptionsTabs.setEnabledAt(index, enabled);
      }
      index++;
    }

    myOtherIndentOptions.setEnabled(enabled);
    myIndentOptionsTabs.setEnabledAt(index, enabled);
  }

  private JPanel createTabOptionsPanel() {
    OptionGroup optionGroup = new OptionGroup(ApplicationBundle.message("group.tabs.and.indents"));

    myCbUseSameIndents = new JCheckBox(ApplicationBundle.message("checkbox.indent.use.same.settings.for.all.file.types"));
    optionGroup.add(myCbUseSameIndents);

    myIndentOptionsTabs = new TabbedPaneWrapper(JTabbedPane.RIGHT);

    for(Map.Entry<FileType, IndentOptionsEditor> entry:myAdditionalIndentOptions.entrySet()) {
      myIndentOptionsTabs.addTab(entry.getKey().getName(), entry.getValue().createPanel());
    }

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
    myOtherIndentOptions.apply(settings, settings.OTHER_INDENT_OPTIONS);

    for(FileType fileType: myAdditionalIndentOptions.keySet()) {
      myAdditionalIndentOptions.get(fileType).apply(settings, settings.getAdditionalIndentOptions(fileType));
    }

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

  @Nullable
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
    if (myOtherIndentOptions.isModified(settings, settings.OTHER_INDENT_OPTIONS)) {
      return true;
    }

    for(FileType fileType: myAdditionalIndentOptions.keySet()) {
      if (myAdditionalIndentOptions.get(fileType).isModified(settings, settings.getAdditionalIndentOptions(fileType))) {
        return true;
      }
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

    myOtherIndentOptions.reset(settings, settings.OTHER_INDENT_OPTIONS);

    for(FileType fileType: myAdditionalIndentOptions.keySet()) {
      myAdditionalIndentOptions.get(fileType).reset(settings, settings.getAdditionalIndentOptions(fileType));
    }

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

  protected EditorHighlighter createHighlighter(final EditorColorsScheme scheme) {
    return JavaHighlighterFactory.createJavaHighlighter(scheme, LanguageLevel.HIGHEST);
  }

}
