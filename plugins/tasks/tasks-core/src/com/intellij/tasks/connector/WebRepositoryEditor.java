package com.intellij.tasks.connector;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
public class WebRepositoryEditor extends BaseRepositoryEditor<WebRepository> {
  private JBLabel myTasksListURLLabel;
  private JBLabel myTaskPatternLabel;
  private JTextField myTasksListURLText;
  private JTextField myTaskPatternText;
  private JBLabel myLoginURLLabel;
  private JTextField myLoginURLText;

  public WebRepositoryEditor(final Project project, final WebRepository repository, final Consumer<WebRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setText("{serverUrl}:");
    myUsernameLabel.setText("{username}:");
    myPasswordLabel.setText("{password}:");

    //setAnchor(myUsernameLabel);
  }

  @Override
  public void apply() {
    myRepository.setTasksListURL(myTasksListURLText.getText());
    myRepository.setTaskPattern(myTaskPatternText.getText());
    myRepository.setLoginURL(myLoginURLText.getText());
    super.apply();
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myTasksListURLLabel = new JBLabel("Tasks List URL:", SwingConstants.RIGHT);
    myTasksListURLText = new JTextField(myRepository.getTasksListURL());
    installListener(myTasksListURLText);
    myTaskPatternLabel = new JBLabel("Task Pattern:", SwingConstants.RIGHT);
    myTaskPatternText = new JTextField(myRepository.getTaskPattern());
    installListener(myTaskPatternText);
    myLoginURLLabel = new JBLabel("Login URL:", SwingConstants.RIGHT);
    myLoginURLText = new JTextField(myRepository.getLoginURL());
    installListener(myLoginURLText);
    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true)
      .addVerticalGap(8)
      .addLabeledComponent(myTasksListURLLabel, myTasksListURLText)
      .addLabeledComponent(new JLabel(), new JBLabel("Available placeholders: " + WebRepository.SERVER_URL_PLACEHOLDER, UIUtil.ComponentStyle.SMALL,
                                UIUtil.FontColor.BRIGHTER), 1)
      .addLabeledComponent(myTaskPatternLabel, myTaskPatternText, 8)
      .addLabeledComponent(new JLabel(), new JBLabel("Task pattern should be a regexp with two matching group: ({id}.+?) and ({summary}.+?)",
                                UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER), 1)
      .addLabeledComponent(myLoginURLLabel, myLoginURLText, 8)
      .addLabeledComponent(new JLabel(), new JBLabel("Available placeholders: " + WebRepository.SERVER_URL_PLACEHOLDER + ", " +
                                WebRepository.USERNAME_PLACEHOLDER + ", " + WebRepository.PASSWORD_PLACEHOLDER, UIUtil.ComponentStyle.SMALL,
                                UIUtil.FontColor.BRIGHTER), 1)
      .addVerticalGap(8)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myTasksListURLLabel.setAnchor(anchor);
    myTaskPatternLabel.setAnchor(anchor);
    myLoginURLLabel.setAnchor(anchor);
  }
}
