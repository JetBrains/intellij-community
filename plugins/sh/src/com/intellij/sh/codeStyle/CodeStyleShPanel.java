package com.intellij.sh.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.formatter.ShShfmtFormatterUtil;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CodeStyleShPanel extends CodeStyleAbstractPanel {
  private static final String BROWSE_FORMATTER_TITLE = "Choose Path to the Shfmt Formatter:";
  private static final String LINK_TITLE = "Download shfmt formatter";

  private JPanel myPanel;
  private JPanel myRightPanel;
  private JPanel myWarningPanel;

  private JCheckBox myTabCharacter;
  private IntegerField myIndentField;
  private JLabel myIndentLabel;
  private JLabel myWarningLabel;

  private JCheckBox myBinaryOpsStartLine;
  private JCheckBox mySwitchCasesIndented;
  private JCheckBox myRedirectFollowedBySpace;
  private JCheckBox myKeepColumnAlignmentPadding;
  private JCheckBox myMinifyProgram;

  @SuppressWarnings("unused")
  private ActionLink myShfmtDownloadLink;
  private TextFieldWithBrowseButton myShfmtPathSelector;

  CodeStyleShPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(ShLanguage.INSTANCE, currentSettings, settings);
    installPreviewPanel(myRightPanel);

    Project project = ProjectUtil.guessCurrentProject(getPanel());
    myShfmtPathSelector.addBrowseFolderListener(BROWSE_FORMATTER_TITLE, "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myShfmtPathSelector.setEditable(false);
    myTabCharacter.addChangeListener(listener -> {
      if (myTabCharacter.isSelected()) {
        myIndentField.setEnabled(false);
        myIndentLabel.setEnabled(false);
      }
      else {
        myIndentField.setEnabled(true);
        myIndentLabel.setEnabled(true);
      }
    });
    myWarningLabel.setIcon(AllIcons.General.Warning);

    addPanelToWatch(myPanel);
  }

  private void createUIComponents() {
    myIndentField = new IntegerField(null, CodeStyleConstraints.MIN_INDENT_SIZE, CodeStyleConstraints.MAX_INDENT_SIZE);
    myShfmtDownloadLink = new ActionLink(LINK_TITLE, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        CodeStyleSettings settings = getSettings();
        ShShfmtFormatterUtil.download(event.getProject(), settings, getPanel());

        ShCodeStyleSettings bashSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
        myShfmtPathSelector.setText(bashSettings.SHFMT_PATH);
      }
    });
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Nullable
  @Override
  protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ShLanguage.INSTANCE, null, null);
    return HighlighterFactory.createHighlighter(highlighter, scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return ShFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    indentOptions.INDENT_SIZE = myIndentField.getValue();
    indentOptions.USE_TAB_CHARACTER = myTabCharacter.isSelected();

    ShCodeStyleSettings bashSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    bashSettings.BINARY_OPS_START_LINE = myBinaryOpsStartLine.isSelected();
    bashSettings.SWITCH_CASES_INDENTED = mySwitchCasesIndented.isSelected();
    bashSettings.REDIRECT_FOLLOWED_BY_SPACE = myRedirectFollowedBySpace.isSelected();
    bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING = myKeepColumnAlignmentPadding.isSelected();
    bashSettings.MINIFY_PROGRAM = myMinifyProgram.isSelected();
    bashSettings.SHFMT_PATH = myShfmtPathSelector.getText();
    myWarningPanel.setVisible(!ShShfmtFormatterUtil.isValidPath(myShfmtPathSelector.getText()));
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    ShCodeStyleSettings bashSettings = settings.getCustomSettings(ShCodeStyleSettings.class);

    return isFieldModified(myBinaryOpsStartLine, bashSettings.BINARY_OPS_START_LINE)
        || isFieldModified(mySwitchCasesIndented, bashSettings.SWITCH_CASES_INDENTED)
        || isFieldModified(myRedirectFollowedBySpace, bashSettings.REDIRECT_FOLLOWED_BY_SPACE)
        || isFieldModified(myKeepColumnAlignmentPadding, bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING)
        || isFieldModified(myMinifyProgram, bashSettings.MINIFY_PROGRAM)
        || isFieldModified(myTabCharacter, indentOptions.USE_TAB_CHARACTER)
        || isFieldModified(myIndentField, indentOptions.INDENT_SIZE)
        || isFieldModified(myShfmtPathSelector, bashSettings.SHFMT_PATH);
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    myIndentField.setValue(indentOptions.INDENT_SIZE);
    myTabCharacter.setSelected(indentOptions.USE_TAB_CHARACTER);

    ShCodeStyleSettings bashSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    myBinaryOpsStartLine.setSelected(bashSettings.BINARY_OPS_START_LINE);
    mySwitchCasesIndented.setSelected(bashSettings.SWITCH_CASES_INDENTED);
    myRedirectFollowedBySpace.setSelected(bashSettings.REDIRECT_FOLLOWED_BY_SPACE);
    myKeepColumnAlignmentPadding.setSelected(bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING);
    myMinifyProgram.setSelected(bashSettings.MINIFY_PROGRAM);
    myShfmtPathSelector.setText(bashSettings.SHFMT_PATH);
    myWarningPanel.setVisible(!ShShfmtFormatterUtil.isValidPath(bashSettings.SHFMT_PATH));
  }

  private boolean isFieldModified(JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private boolean isFieldModified(IntegerField textField, int value) {
    return textField.getValue() != value;
  }

  private boolean isFieldModified(TextFieldWithBrowseButton browseButton, String value) {
    return !browseButton.getText().equals(value);
  }

  private static final String GENERAL_CODE_SAMPLE = "#!/usr/bin/env sh\n" +
      "\n" +
      "function foo() {\n" +
      "  if [ -x $file ]; then\n" +
      "    myArray=(item1 item2 item3)\n" +
      "  elif [ $file1 -nt $file2 ]; then\n" +
      "    unset myArray\n" +
      "  else\n" +
      "    echo \"Usage: $0 file ...\"\n" +
      "  fi\n" +
      "}\n" +
      "\n" +
      "for (( i = 0; i < 5; i++ )); do\n" +
      "  read -p r\n" +
      "  print -n $r\n" +
      "  wait $!\n" +
      "done\n";
}
