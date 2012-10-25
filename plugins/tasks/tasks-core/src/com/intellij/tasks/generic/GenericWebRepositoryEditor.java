package com.intellij.tasks.generic;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import static com.intellij.tasks.generic.GenericWebRepository.*;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
public class GenericWebRepositoryEditor extends BaseRepositoryEditor<GenericWebRepository> {
  public static final String POST = "POST";
  public static final String GET = "GET";
  private EditorTextField myTasksListURLText;
  private Editor myTaskPatternText;
  private JBLabel myLoginURLLabel;
  private EditorTextField myLoginURLText;
  private JPanel myTaskPatternPanel;
  private ComboBox myLoginMethodType;
  private ComboBox myGetTasksMethodType;
  private JBLabel myLoginTooltip;
  private JBLabel myTaskListTooltip;
  private JBLabel myTaskPatternTooltip;
  private JPanel myPanel;
  private JBLabel myTaskPatternLabel;

  public GenericWebRepositoryEditor(final Project project,
                                    final GenericWebRepository repository,
                                    final Consumer<GenericWebRepository> changeListener) {
    super(project, repository, changeListener);

    myLoginAnonymouslyJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loginAnonymouslyChanged(!myLoginAnonymouslyJBCheckBox.isSelected());
      }
    });

    installListener(myTasksListURLText.getDocument());
    installListener(myLoginURLText.getDocument());

    myLoginMethodType.setSelectedItem(myRepository.getLoginMethodType());
    installListener(myLoginMethodType);

    myGetTasksMethodType.setSelectedItem(myRepository.getGetTasksMethodType());
    installListener(myGetTasksMethodType);

    //todo completion
    //todo completion without whitespace before cursor
    final Document document = EditorFactory.getInstance().createDocument(myRepository.getTaskPattern());
    myTaskPatternText = EditorFactory.getInstance().createEditor(document, myProject, HtmlFileType.INSTANCE, false);
    final ArrayList<String> completionList = ContainerUtil.newArrayList("({id}.+?)", "({summary}.+?)");
    //TextFieldWithAutoCompletionContributor.installCompletion(document, myProject,
    //                                                          new StringsCompletionProvider(completionList, null), true);
    installListener(document);
    myTaskPatternPanel.add(myTaskPatternText.getComponent(), BorderLayout.CENTER) ;
    myTaskPatternText.getSettings().setLineMarkerAreaShown(false);
    myTaskPatternText.getSettings().setFoldingOutlineShown(false);
    myTaskPatternText.getSettings().setLineNumbersShown(false);
    myTaskPatternText.getSettings().setWhitespacesShown(true);
    myTaskPatternText.getSettings().setRightMarginShown(false);
    myTaskPatternText.getSettings().setUseSoftWraps(true);
    myTaskPatternText.getSettings().setAdditionalLinesCount(0);
    myTaskPatternText.getSettings().setAdditionalPageAtBottom(false);

    myTaskPatternLabel.setLabelFor(myTaskPatternText.getContentComponent());

    String useCompletionText = ". Use " +
                               KeymapUtil
                                 .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION)) +
                               " for completion.";
    myLoginTooltip.setText("<html>Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + USERNAME_PLACEHOLDER + ", " +
                             PASSWORD_PLACEHOLDER + useCompletionText + "</html>");
    myTaskListTooltip.setText("<html>Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + MAX_COUNT_PLACEHOLDER + ", " +
                              QUERY_PLACEHOLDER + " (use for faster tasks search)" + useCompletionText + "</html>");
    myTaskPatternTooltip.setText(
      "<html>Task pattern should be a regexp with two matching groups: ({id}.+?) and ({summary}.+?)" + useCompletionText + "</html>");

    myTabbedPane.addTab("Additional", myPanel);

    loginAnonymouslyChanged(!myLoginAnonymouslyJBCheckBox.isSelected());
  }

  private void loginAnonymouslyChanged(boolean enabled) {
    myLoginURLLabel.setEnabled(enabled);
    myLoginURLText.setEnabled(enabled);
    myLoginMethodType.setEnabled(enabled);
    myLoginTooltip.setEnabled(enabled);
  }

  @Override
  public void apply() {
    myRepository.setTasksListURL(myTasksListURLText.getText());
    myRepository.setTaskPattern(myTaskPatternText.getDocument().getText());
    myRepository.setLoginURL(myLoginURLText.getText());
    myRepository.setLoginMethodType((String)myLoginMethodType.getModel().getSelectedItem());
    myRepository.setGetTasksMethodType((String)myGetTasksMethodType.getModel().getSelectedItem());
    super.apply();
  }

  private void createUIComponents() {
    final ArrayList<String> completionList = ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, USERNAME_PLACEHOLDER, PASSWORD_PLACEHOLDER);
    myLoginURLText = TextFieldWithAutoCompletion.create(myProject, completionList, null, false, myRepository.getLoginURL());

    final ArrayList<String> completionList1 = ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, QUERY_PLACEHOLDER, MAX_COUNT_PLACEHOLDER);
    myTasksListURLText = TextFieldWithAutoCompletion.create(myProject, completionList1, null, false, myRepository.getTasksListURL());
  }

  @Override
  public void dispose() {
    super.dispose();
    EditorFactory.getInstance().releaseEditor(myTaskPatternText);
  }
}
