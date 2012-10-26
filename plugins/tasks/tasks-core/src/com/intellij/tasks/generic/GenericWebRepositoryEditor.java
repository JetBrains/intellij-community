package com.intellij.tasks.generic;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionContributor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
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
  private EditorTextField myTaskPatternText;
  private JBLabel myLoginURLLabel;
  private EditorTextField myLoginURLText;
  private ComboBox myLoginMethodType;
  private ComboBox myTasksListMethodType;
  private JBLabel myLoginTooltip;
  private JBLabel myTaskListTooltip;
  private JBLabel myTaskPatternTooltip;
  private JPanel myPanel;
  private JRadioButton myXmlRadioButton;
  private JRadioButton myHtmlRadioButton;
  private JButton myTest2Button;
  private JRadioButton myJsonRadioButton;

  public GenericWebRepositoryEditor(final Project project,
                                    final GenericWebRepository repository,
                                    final Consumer<GenericWebRepository> changeListener) {
    super(project, repository, changeListener);

    myTest2Button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        afterTestConnection(TaskManager.getManager(project).testConnection(repository));
      }
    });

    myLoginAnonymouslyJBCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loginUrlEnablingChanged();
      }
    });

    myUseHTTPAuthentication.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loginUrlEnablingChanged();
      }
    });

    switch (myRepository.getResponseType()) {
      case XML:
        myXmlRadioButton.setSelected(true);
        myHtmlRadioButton.setSelected(false);
        myJsonRadioButton.setSelected(false);
        break;
      case HTML:
        myXmlRadioButton.setSelected(false);
        myHtmlRadioButton.setSelected(true);
        myJsonRadioButton.setSelected(false);
        break;
      case JSON:
        myXmlRadioButton.setSelected(false);
        myHtmlRadioButton.setSelected(false);
        myJsonRadioButton.setSelected(true);
        break;
    }

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        responseTypeChanged();
      }
    };
    myXmlRadioButton.addActionListener(listener);
    myHtmlRadioButton.addActionListener(listener);
    myJsonRadioButton.addActionListener(listener);

    myLoginMethodType.setSelectedItem(myRepository.getLoginMethodType());
    myTasksListMethodType.setSelectedItem(myRepository.getTasksListMethodType());

    installListener(myLoginMethodType);
    installListener(myTasksListMethodType);
    installListener(myTasksListURLText.getDocument());
    installListener(myLoginURLText.getDocument());
    installListener(myTaskPatternText.getDocument());

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

    loginUrlEnablingChanged();
  }

  private void responseTypeChanged() {
    doApply();
    myTaskPatternText.setFileType(myRepository.getResponseType().getFileType());
  }

  private void loginUrlEnablingChanged() {
    final boolean enabled = !myLoginAnonymouslyJBCheckBox.isSelected() && !myUseHTTPAuthentication.isSelected();
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
    myRepository.setTasksListMethodType((String)myTasksListMethodType.getModel().getSelectedItem());
    myRepository.setResponseType(
      myXmlRadioButton.isSelected() ? ResponseType.XML : myJsonRadioButton.isSelected() ? ResponseType.JSON : ResponseType.HTML);
    super.apply();
  }

  private void createUIComponents() {
    //todo completion
    //todo completion without whitespace before cursor
    final ArrayList<String> completionList = ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, USERNAME_PLACEHOLDER, PASSWORD_PLACEHOLDER);
    myLoginURLText = TextFieldWithAutoCompletion.create(myProject, completionList, null, false, myRepository.getLoginURL());

    final ArrayList<String> completionList1 = ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, QUERY_PLACEHOLDER, MAX_COUNT_PLACEHOLDER);
    myTasksListURLText = TextFieldWithAutoCompletion.create(myProject, completionList1, null, false, myRepository.getTasksListURL());

    final Document document = EditorFactory.getInstance().createDocument(myRepository.getTaskPattern());
    myTaskPatternText = new EditorTextField(document, myProject, myRepository.getResponseType().getFileType(), false, false);
    final ArrayList<String> completionList2 = ContainerUtil.newArrayList("({id}.+?)", "({summary}.+?)");
    TextFieldWithAutoCompletionContributor
      .installCompletion(document, myProject, new TextFieldWithAutoCompletion.StringsCompletionProvider(completionList2, null), true);
    myTaskPatternText.setFontInheritedFromLAF(false);
  }
}
