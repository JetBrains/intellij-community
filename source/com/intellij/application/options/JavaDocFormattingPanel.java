package com.intellij.application.options;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.psi.codeStyle.CodeStyleSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author max
 */
public class JavaDocFormattingPanel extends JPanel {
  private SettingsPanel mySettingsPanel;
  private JCheckBox myEnableCheckBox;
  private CodeStyleSettings mySettings;

  public JavaDocFormattingPanel(CodeStyleSettings settings) {
    super(new BorderLayout());
    mySettingsPanel = new SettingsPanel(settings);
    myEnableCheckBox = new JCheckBox("Enable JavaDoc formatting");
    mySettings = settings;
    myEnableCheckBox.setSelected(mySettings.ENABLE_JAVADOC_FORMATTING);
    update();
    myEnableCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        update();
      }
    });

    add(myEnableCheckBox, BorderLayout.NORTH);
    add(mySettingsPanel, BorderLayout.CENTER);
  }

  public void dispose() {
    mySettingsPanel.dispose();
  }

  public void apply() {
    mySettings.ENABLE_JAVADOC_FORMATTING = myEnableCheckBox.isSelected();
    mySettingsPanel.apply();
    update();
  }

  public void reset() {
    myEnableCheckBox.setSelected(mySettings.ENABLE_JAVADOC_FORMATTING);
    mySettingsPanel.reset();
    update();
  }

  public boolean isModified() {
    return myEnableCheckBox.isSelected() != mySettings.ENABLE_JAVADOC_FORMATTING || mySettingsPanel.isModified();
  }

  private void update() {
    boolean enabled = myEnableCheckBox.isSelected();
    setEnabled(mySettingsPanel, enabled);
    boolean saved = mySettings.ENABLE_JAVADOC_FORMATTING;
    mySettings.ENABLE_JAVADOC_FORMATTING = enabled;
    mySettingsPanel.updatePreview();
    mySettings.ENABLE_JAVADOC_FORMATTING = saved;
  }

  private class SettingsPanel extends OptionTreeWithPreviewPanel {
    public SettingsPanel(CodeStyleSettings settings) {
      super(settings);
    }

    protected void initTables() {
      initBooleanField("JD_ALIGN_PARAM_COMMENTS", "Align parameter descriptions", "Alignment");
      initBooleanField("JD_ALIGN_EXCEPTION_COMMENTS", "Align thrown exception descriptions", "Alignment");

      initBooleanField("JD_ADD_BLANK_AFTER_DESCRIPTION", "After description", "Blank lines");
      initBooleanField("JD_ADD_BLANK_AFTER_PARM_COMMENTS", "After parameter descriptions", "Blank lines");
      initBooleanField("JD_ADD_BLANK_AFTER_RETURN", "After return tag", "Blank lines");

      initBooleanField("JD_KEEP_INVALID_TAGS", "Keep invalid tags", "Invalid tags");
      initBooleanField("JD_KEEP_EMPTY_PARAMETER", "Keep empty @param tags", "Invalid tags");
      initBooleanField("JD_KEEP_EMPTY_RETURN", "Keep empty @return tags", "Invalid tags");
      initBooleanField("JD_KEEP_EMPTY_EXCEPTION", "Keep empty @throws tags", "Invalid tags");

      initBooleanField("JD_USE_THROWS_NOT_EXCEPTION", "Use @throws rather than @exception", "Other");
      initBooleanField("WRAP_COMMENTS", "Wrap at right margin", "Other");
      initBooleanField("JD_P_AT_EMPTY_LINES", "Generate \"<p/>\" on empty lines", "Other");
      initBooleanField("JD_KEEP_EMPTY_LINES", "Keep empty lines", "Other");
      initBooleanField("JD_DO_NOT_WRAP_ONE_LINE_COMMENTS", "Do not wrap one line comments", "Other" );
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
      return 47;
    }

    protected String getPreviewText() {                    //| Margin is here
      return "package sample;\n" +
             "public class Sample {\n" +
             "  /**\n" +
             "   * This is a method description that is long enough to exceed right margin.\n" +
             "   *\n" +
             "   * Another paragraph of the description placed after blank line.\n" +
             "   * @param i short named parameter description\n" +
             "   * @param longParameterName long named parameter description\n" +
             "   * @param missingDescription\n" +
             "   * @return return description.\n" +
             "   * @throws XXXException description.\n" +
             "   * @throws YException description.\n" +
             "   * @throws ZException\n" +
             "   *\n" +
             "   * @invalidTag" +
             "   */\n" +
             "  public abstract String sampleMethod(int i, int longParameterName, int missingDescription) throws XXXException, YException, ZException;\n" +
             "\n"+
             "  /** One-line comment */\n" +
             "  public abstract String sampleMethod2();\n";
    }
  }

  private static void setEnabled(JComponent c, boolean enabled) {
    c.setEnabled(enabled);
    Component[] children = c.getComponents();
    for (int i = 0; i < children.length; i++) {
      Component child = children[i];
      if (child instanceof JComponent) {
        setEnabled((JComponent)child, enabled);
      }
    }
  }
}
