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
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.TextFieldWithAutoCompletionContributor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

import static com.intellij.tasks.generic.GenericWebRepository.*;
import static com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;

/**
 * User: Evgeny.Zakrevsky
 * Date: 10/4/12
 */
public class GenericWebRepositoryEditor extends BaseRepositoryEditor<GenericWebRepository> {
  public static final String POST = "POST";
  public static final String GET = "GET";
  private JBLabel myTasksListURLLabel;
  private JBLabel myTaskPatternLabel;
  private TextFieldWithAutoCompletion<String> myTasksListURLText;
  private Editor myTaskPatternText;
  private JBLabel myLoginURLLabel;
  private TextFieldWithAutoCompletion<String> myLoginURLText;
  private ComboBox myLoginMethodType;
  private ComboBox myGetTasksMethodType;

  public GenericWebRepositoryEditor(final Project project,
                                    final GenericWebRepository repository,
                                    final Consumer<GenericWebRepository> changeListener) {
    super(project, repository, changeListener);
    myUrlLabel.setText("Server URL");
  }

  @Override
  protected void loginAnonymouslyChanged(boolean enabled) {
    super.loginAnonymouslyChanged(enabled);
    myLoginURLLabel.setEnabled(enabled);
    myLoginURLText.setEnabled(enabled);
    myLoginMethodType.setEnabled(enabled);
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

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myLoginURLLabel = new JBLabel("Login URL:", SwingConstants.RIGHT);
    myLoginURLText = TextFieldWithAutoCompletion
      .create(myProject, ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, USERNAME_PLACEHOLDER, PASSWORD_PLACEHOLDER), null, false,
              myRepository.getLoginURL());
    installListener(myLoginURLText.getDocument());
    myLoginMethodType = new ComboBox(new String[]{GET, POST}, -1);
    myLoginMethodType.setSelectedItem(myRepository.getLoginMethodType());
    installListener(myLoginMethodType);
    JPanel loginPanel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    loginPanel.add(myLoginURLText, BorderLayout.CENTER);
    loginPanel.add(myLoginMethodType, BorderLayout.EAST);

    myTasksListURLLabel = new JBLabel("Tasks List URL:", SwingConstants.RIGHT);
    final ArrayList<String> completionList1 = ContainerUtil.newArrayList(SERVER_URL_PLACEHOLDER, QUERY_PLACEHOLDER, MAX_COUNT_PLACEHOLDER);
    myTasksListURLText = TextFieldWithAutoCompletion.create(myProject, completionList1, null, false, myRepository.getTasksListURL());
    installListener(myTasksListURLText.getDocument());
    myGetTasksMethodType = new ComboBox(new String[]{GET, POST}, -1);
    myGetTasksMethodType.setSelectedItem(myRepository.getGetTasksMethodType());
    installListener(myGetTasksMethodType);
    JPanel tasksPanel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
    tasksPanel.add(myTasksListURLText, BorderLayout.CENTER);
    tasksPanel.add(myGetTasksMethodType, BorderLayout.EAST);

    myTaskPatternLabel = new JBLabel("Task Pattern:", SwingConstants.RIGHT);
    final Document document = EditorFactory.getInstance().createDocument(myRepository.getTaskPattern());
    myTaskPatternText = EditorFactory.getInstance().createEditor(document, myProject, HtmlFileType.INSTANCE, false);
    final ArrayList<String> completionList2 = ContainerUtil.newArrayList("({id}.+?)", "({summary}.+?)");
    TextFieldWithAutoCompletionContributor
      .installCompletion(document, myProject, new StringsCompletionProvider(completionList2, null), true);
    installListener(document);
    myTaskPatternText.getSettings().setLineMarkerAreaShown(false);
    myTaskPatternText.getSettings().setFoldingOutlineShown(false);
    //todo correct resizing
    //todo completion
    //todo completion without whitespace before cursor

    String useCompletionText = ". Use " +
                               KeymapUtil
                                 .getFirstKeyboardShortcutText(ActionManager.getInstance().getAction(IdeActions.ACTION_CODE_COMPLETION)) +
                               " for completion.";

    return FormBuilder.createFormBuilder().setAlignLabelOnRight(true)
      .addLabeledComponent(myLoginURLLabel, loginPanel)
      .addTooltip(
        "<html>Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + USERNAME_PLACEHOLDER + ", " + PASSWORD_PLACEHOLDER +
        useCompletionText + "</html>")
      .addLabeledComponent(myTasksListURLLabel, tasksPanel)
      .addTooltip(
        "<html>Available placeholders: " + SERVER_URL_PLACEHOLDER + ", " + MAX_COUNT_PLACEHOLDER + ", " + QUERY_PLACEHOLDER +
        " (use for faster tasks search)" + useCompletionText + "</html>")
      .addLabeledComponent(myTaskPatternLabel, myTaskPatternText.getComponent())
      .addTooltip(
        "<html>Task pattern should be a regexp with two matching groups: ({id}.+?) and ({summary}.+?)" + useCompletionText + "</html>")
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
