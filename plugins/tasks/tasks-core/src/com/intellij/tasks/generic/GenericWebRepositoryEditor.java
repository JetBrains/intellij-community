package com.intellij.tasks.generic;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.tasks.generic.GenericWebRepository.*;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
public class GenericWebRepositoryEditor extends BaseRepositoryEditor<GenericWebRepository> {
  private JBLabel myTasksListURLLabel;
  private JBLabel myTaskPatternLabel;
  private TextFieldWithAutoCompletion<String> myTasksListURLText;
  private TextFieldWithAutoCompletion<String> myTaskPatternText;
  private JBLabel myLoginURLLabel;
  private TextFieldWithAutoCompletion<String> myLoginURLText;

  public GenericWebRepositoryEditor(final Project project,
                                    final GenericWebRepository repository,
                                    final Consumer<GenericWebRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setText("{serverUrl}:");
    myUsernameLabel.setText("{username}:");
    myPasswordLabel.setText("{password}:");
  }

  @Override
  protected void loginAnonymouslyChanged(boolean enabled) {
    super.loginAnonymouslyChanged(enabled);
    myLoginURLLabel.setEnabled(enabled);
    myLoginURLText.setEnabled(enabled);
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
    myLoginURLLabel = new JBLabel("Login URL:", SwingConstants.RIGHT);
    myLoginURLText = TextFieldWithAutoCompletion
      .create(myProject, ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, USERNAME_PLACEHOLDER, PASSWORD_PLACEHOLDER), null, false,
              myRepository.getLoginURL());
    installListener(myLoginURLText.getDocument());

    myTasksListURLLabel = new JBLabel("Tasks List URL:", SwingConstants.RIGHT);
    myTasksListURLText = TextFieldWithAutoCompletion.create(myProject, ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER), null, false,
                                                            myRepository.getTasksListURL());
    installListener(myTasksListURLText.getDocument());

    myTaskPatternLabel = new JBLabel("Task Pattern:", SwingConstants.RIGHT);
    myTaskPatternText =
      TextFieldWithAutoCompletion
        .create(myProject, ContainerUtil.newArrayList("({id}.+?)", "({summary}.+?)"), null, false, myRepository.getTaskPattern());
    installListener(myTaskPatternText.getDocument());

    String useCompletionText = ". Use " +
                               KeymapUtil
                                 .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION)) +
                               " for completion.";

    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true)
      .addLabeledComponent(myLoginURLLabel, myLoginURLText)
      .addTooltip(
        "Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + USERNAME_PLACEHOLDER + ", " + PASSWORD_PLACEHOLDER + useCompletionText)
      .addLabeledComponent(myTasksListURLLabel, myTasksListURLText)
      .addTooltip("Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + QUERY_PLACEHOLDER + " (use for faster tasks search)" + useCompletionText)
      .addLabeledComponent(myTaskPatternLabel, myTaskPatternText)
      .addTooltip("Task pattern should be a regexp with two matching group: ({id}.+?) and ({summary}.+?)" + useCompletionText)
      .getPanel();
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myTasksListURLText != null && myTasksListURLText.getEditor() != null)
      EditorFactory.getInstance().releaseEditor(myTasksListURLText.getEditor());
    if (myLoginURLText.getEditor() != null) EditorFactory.getInstance().releaseEditor(myLoginURLText.getEditor());
    if (myTaskPatternText.getEditor() != null) EditorFactory.getInstance().releaseEditor(myTaskPatternText.getEditor());
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myTasksListURLLabel.setAnchor(anchor);
    myTaskPatternLabel.setAnchor(anchor);
    myLoginURLLabel.setAnchor(anchor);
  }
}
