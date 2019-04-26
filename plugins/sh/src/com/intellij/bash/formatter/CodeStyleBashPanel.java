package com.intellij.bash.formatter;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.bash.BashFileType;
import com.intellij.bash.BashLanguage;
import com.intellij.bash.BashSyntaxHighlighter;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.ui.components.fields.IntegerField;
import com.intellij.ui.components.labels.ActionLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CodeStyleBashPanel extends CodeStyleAbstractPanel {

  private static final String BASH_SHFMT_PATH_KEY = "bash.shfmt.path";
  private static final String BROWSE_FOLDER_TITLE = "Choose path to the shfmt formatter:";
  private static final String LINK_TITLE = "Download shfmt formatter";

  private JPanel myPanel;
  private JPanel myRightPanel;

  private JCheckBox myTabCharacter;
  private IntegerField myIndentField;
  private JLabel myIndentLabel;

  private JCheckBox myBinaryOpsStartLine;
  private JCheckBox mySwitchCasesIndented;
  private JCheckBox myRedirectFollowedBySpace;
  private JCheckBox myKeepColumnAlignmentPadding;
  private JCheckBox myMinifyProgram;

  private ActionLink myShfmtDownloadLink;
  private TextFieldWithBrowseButton myShfmtPathSelector;

  CodeStyleBashPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(BashLanguage.INSTANCE, currentSettings, settings);
    installPreviewPanel(myRightPanel);

    Project project = ProjectUtil.guessCurrentProject(getPanel());
    myShfmtPathSelector.addBrowseFolderListener(BROWSE_FOLDER_TITLE, "", project, FileChooserDescriptorFactory.createSingleFileDescriptor());
    myTabCharacter.addChangeListener(e -> {
      if (myTabCharacter.isSelected()) {
        myIndentField.setEnabled(false);
        myIndentLabel.setEnabled(false);
      }
      else {
        myIndentField.setEnabled(true);
        myIndentLabel.setEnabled(true);
      }
    });
    addPanelToWatch(myPanel);
  }

  private void createUIComponents() {
    myIndentField = new IntegerField(null, CodeStyleConstraints.MIN_INDENT_SIZE, CodeStyleConstraints.MAX_INDENT_SIZE);
    myShfmtDownloadLink = new ActionLink(LINK_TITLE, new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("Clicked");
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
    return HighlighterFactory.createHighlighter(new BashSyntaxHighlighter(), scheme);
  }

  @NotNull
  @Override
  protected FileType getFileType() {
    return BashFileType.INSTANCE;
  }

  @Nullable
  @Override
  protected String getPreviewText() {
    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public void apply(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    indentOptions.INDENT_SIZE = myIndentField.getValue();
    indentOptions.USE_TAB_CHARACTER = myTabCharacter.isSelected();

    BashCodeStyleSettings bashSettings = settings.getCustomSettings(BashCodeStyleSettings.class);
    bashSettings.BINARY_OPS_START_LINE = myBinaryOpsStartLine.isSelected();
    bashSettings.SWITCH_CASES_INDENTED = mySwitchCasesIndented.isSelected();
    bashSettings.REDIRECT_FOLLOWED_BY_SPACE = myRedirectFollowedBySpace.isSelected();
    bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING = myKeepColumnAlignmentPadding.isSelected();
    bashSettings.MINIFY_PROGRAM = myMinifyProgram.isSelected();
    Registry.get(BASH_SHFMT_PATH_KEY).setValue(myShfmtPathSelector.getText());
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    BashCodeStyleSettings bashSettings = settings.getCustomSettings(BashCodeStyleSettings.class);

    return isFieldModified(myBinaryOpsStartLine, bashSettings.BINARY_OPS_START_LINE)
        || isFieldModified(mySwitchCasesIndented, bashSettings.SWITCH_CASES_INDENTED)
        || isFieldModified(myRedirectFollowedBySpace, bashSettings.REDIRECT_FOLLOWED_BY_SPACE)
        || isFieldModified(myKeepColumnAlignmentPadding, bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING)
        || isFieldModified(myMinifyProgram, bashSettings.MINIFY_PROGRAM)
        || isFieldModified(myTabCharacter, indentOptions.USE_TAB_CHARACTER)
        || isFieldModified(myIndentField, indentOptions.INDENT_SIZE)
        || isFieldModified(myShfmtPathSelector, Registry.stringValue(BASH_SHFMT_PATH_KEY));
  }

  @Nullable
  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getIndentOptions();
    myIndentField.setValue(indentOptions.INDENT_SIZE);
    myTabCharacter.setSelected(indentOptions.USE_TAB_CHARACTER);

    BashCodeStyleSettings bashSettings = settings.getCustomSettings(BashCodeStyleSettings.class);
    myBinaryOpsStartLine.setSelected(bashSettings.BINARY_OPS_START_LINE);
    mySwitchCasesIndented.setSelected(bashSettings.SWITCH_CASES_INDENTED);
    myRedirectFollowedBySpace.setSelected(bashSettings.REDIRECT_FOLLOWED_BY_SPACE);
    myKeepColumnAlignmentPadding.setSelected(bashSettings.KEEP_COLUMN_ALIGNMENT_PADDING);
    myMinifyProgram.setSelected(bashSettings.MINIFY_PROGRAM);
    myShfmtPathSelector.setText(Registry.stringValue(BASH_SHFMT_PATH_KEY));
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

  private static final String GENERAL_CODE_SAMPLE = "#!/usr/bin/env bash\n" +
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
