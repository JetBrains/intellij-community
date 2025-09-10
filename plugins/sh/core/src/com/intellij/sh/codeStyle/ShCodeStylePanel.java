// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeStyle;

import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.codeStyle.CodeStyleConstraints;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.sh.ShBundle;
import com.intellij.sh.ShFileType;
import com.intellij.sh.ShLanguage;
import com.intellij.sh.formatter.ShFormatterDownloader;
import com.intellij.sh.settings.ShSettings;
import com.intellij.sh.utils.ProjectUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.fields.IntegerField;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class ShCodeStylePanel extends CodeStyleAbstractPanel {
  private JPanel myPanel;
  private JPanel myRightPanel;
  private JPanel myWarningPanel;

  private JCheckBox myTabCharacter;
  private IntegerField myIndentField;
  private IntegerField myTabField;
  private JLabel myWarningLabel;
  private JLabel myErrorLabel;

  private JCheckBox myBinaryOpsStartLine;
  private JCheckBox mySwitchCasesIndented;
  private JCheckBox myRedirectFollowedBySpace;
  private JCheckBox myKeepColumnAlignmentPadding;
  private JCheckBox myMinifyProgram;
  private JCheckBox myUnixLineSeparator;

  private final Project myProject;

  @SuppressWarnings("unused")
  private ActionLink myShfmtDownloadLink;
  private TextFieldWithBrowseButton myShfmtPathSelector;

  ShCodeStylePanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(ShLanguage.INSTANCE, currentSettings, settings);
    installPreviewPanel(myRightPanel);

    myProject = ProjectUtil.getProject(getPanel());
    myShfmtPathSelector.addBrowseFolderListener(myProject, FileChooserDescriptorFactory.createSingleFileDescriptor().withTitle(ShBundle.message("sh.code.style.choose.path")));
    myShfmtPathSelector.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent documentEvent) {
        myWarningPanel.setVisible(!ShFormatterDownloader.getInstance().isValidPath(myShfmtPathSelector.getText()));
      }
    });

    myWarningLabel.setIcon(AllIcons.General.Warning);
    myErrorLabel.setForeground(JBColor.RED);

    myBinaryOpsStartLine.setText(ShBundle.message("sh.code.style.binary.ops.like.and.may.start.a.line"));
    mySwitchCasesIndented.setText(ShBundle.message("sh.code.style.switch.cases.will.be.indented"));
    myRedirectFollowedBySpace.setText(ShBundle.message("sh.code.style.redirect.operators.will.be.followed.by.a.space"));
    myKeepColumnAlignmentPadding.setText(ShBundle.message("sh.code.style.keep.column.alignment.padding"));
    myMinifyProgram.setText(ShBundle.message("sh.code.style.minify.program.to.reduce.its.size"));
    myUnixLineSeparator.setText(ShBundle.message("sh.code.style.unix.line.separator"));

    addPanelToWatch(myPanel);
  }

  private void createUIComponents() {
    myIndentField = new IntegerField(null, CodeStyleConstraints.MIN_INDENT_SIZE, CodeStyleConstraints.MAX_INDENT_SIZE);
    myTabField = new IntegerField(null, CodeStyleConstraints.MIN_TAB_SIZE, CodeStyleConstraints.MAX_TAB_SIZE);
    myShfmtDownloadLink = new ActionLink(ShBundle.message("sh.code.style.download.link"), e -> {
        ShFormatterDownloader.getInstance().download(myProject,
                                                     () -> myShfmtPathSelector.setText(ShSettings.getShfmtPath(myProject)),
                                                     () -> myErrorLabel.setVisible(true));
    });
  }

  @Override
  protected int getRightMargin() {
    return 0;
  }

  @Override
  protected @Nullable EditorHighlighter createHighlighter(@NotNull EditorColorsScheme scheme) {
    SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(ShLanguage.INSTANCE, null, null);
    return HighlighterFactory.createHighlighter(highlighter, scheme);
  }

  @Override
  protected @NotNull FileType getFileType() {
    return ShFileType.INSTANCE;
  }

  @Override
  protected @Nullable String getPreviewText() {
    return GENERAL_CODE_SAMPLE;
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    indentOptions.INDENT_SIZE = myIndentField.getValue();
    indentOptions.TAB_SIZE = myTabField.getValue();
    indentOptions.USE_TAB_CHARACTER = myTabCharacter.isSelected();

    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    shSettings.BINARY_OPS_START_LINE = myBinaryOpsStartLine.isSelected();
    shSettings.SWITCH_CASES_INDENTED = mySwitchCasesIndented.isSelected();
    shSettings.REDIRECT_FOLLOWED_BY_SPACE = myRedirectFollowedBySpace.isSelected();
    shSettings.KEEP_COLUMN_ALIGNMENT_PADDING = myKeepColumnAlignmentPadding.isSelected();
    shSettings.MINIFY_PROGRAM = myMinifyProgram.isSelected();
    shSettings.USE_UNIX_LINE_SEPARATOR = myUnixLineSeparator.isSelected();
    ShSettings.setShfmtPath(myProject, myShfmtPathSelector.getText());
    myWarningPanel.setVisible(!ShFormatterDownloader.getInstance().isValidPath(myShfmtPathSelector.getText()));
    myErrorLabel.setVisible(false);
  }

  @Override
  public boolean isModified(CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);

    return isFieldModified(myBinaryOpsStartLine, shSettings.BINARY_OPS_START_LINE)
        || isFieldModified(mySwitchCasesIndented, shSettings.SWITCH_CASES_INDENTED)
        || isFieldModified(myRedirectFollowedBySpace, shSettings.REDIRECT_FOLLOWED_BY_SPACE)
        || isFieldModified(myKeepColumnAlignmentPadding, shSettings.KEEP_COLUMN_ALIGNMENT_PADDING)
        || isFieldModified(myMinifyProgram, shSettings.MINIFY_PROGRAM)
        || isFieldModified(myUnixLineSeparator, shSettings.USE_UNIX_LINE_SEPARATOR)
        || isFieldModified(myTabCharacter, indentOptions.USE_TAB_CHARACTER)
        || isFieldModified(myIndentField, indentOptions.INDENT_SIZE)
        || isFieldModified(myTabField, indentOptions.TAB_SIZE)
        || isFieldModified(myShfmtPathSelector, ShSettings.getShfmtPath(myProject));
  }

  @Override
  public @Nullable JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected void resetImpl(@NotNull CodeStyleSettings settings) {
    CommonCodeStyleSettings.IndentOptions indentOptions = settings.getLanguageIndentOptions(ShLanguage.INSTANCE);
    myIndentField.setValue(indentOptions.INDENT_SIZE);
    myTabField.setValue(indentOptions.TAB_SIZE);
    myTabCharacter.setSelected(indentOptions.USE_TAB_CHARACTER);

    ShCodeStyleSettings shSettings = settings.getCustomSettings(ShCodeStyleSettings.class);
    myBinaryOpsStartLine.setSelected(shSettings.BINARY_OPS_START_LINE);
    mySwitchCasesIndented.setSelected(shSettings.SWITCH_CASES_INDENTED);
    myRedirectFollowedBySpace.setSelected(shSettings.REDIRECT_FOLLOWED_BY_SPACE);
    myKeepColumnAlignmentPadding.setSelected(shSettings.KEEP_COLUMN_ALIGNMENT_PADDING);
    myMinifyProgram.setSelected(shSettings.MINIFY_PROGRAM);
    myUnixLineSeparator.setSelected(shSettings.USE_UNIX_LINE_SEPARATOR);
    myShfmtPathSelector.setText(ShSettings.getShfmtPath(myProject));
    myWarningPanel.setVisible(!ShFormatterDownloader.getInstance().isValidPath(ShSettings.getShfmtPath(myProject)));
    myErrorLabel.setVisible(false);
  }

  private static boolean isFieldModified(@NotNull JCheckBox checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  private static boolean isFieldModified(@NotNull IntegerField textField, int value) {
    return textField.getValue() != value;
  }

  private static boolean isFieldModified(@NotNull TextFieldWithBrowseButton browseButton, String value) {
    return !browseButton.getText().equals(value);
  }

  private static final @NonNls String GENERAL_CODE_SAMPLE = """
    #!/usr/bin/env sh

    function foo() {
      if [ -x $file ]; then
        myArray=(item1 item2 item3)
      elif [ $file1 -nt $file2 ]; then
        unset myArray
      else
        echo "Usage: $0 file ..."
      fi
    }

    for (( i = 0; i < 5; i++ )); do
      read -p r
      print -n $r
      wait $!
    done
    """;
}
