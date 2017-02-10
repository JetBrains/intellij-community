package com.intellij.tasks.generic;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Condition;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HTTPMethod;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.tasks.generic.GenericRepositoryUtil.*;
import static com.intellij.ui.TextFieldWithAutoCompletion.StringsCompletionProvider;

/**
 * @author Evgeny.Zakrevsky
 * @author Mikhail Golubev
 */
public class GenericRepositoryEditor<T extends GenericRepository> extends BaseRepositoryEditor<T> {

  protected EditorTextField myLoginURLText;
  private EditorTextField myTasksListURLText;
  private EditorTextField mySingleTaskURLText;
  protected JBLabel myLoginURLLabel;
  protected ComboBox myLoginMethodTypeComboBox;
  private ComboBox myTasksListMethodTypeComboBox;
  private ComboBox mySingleTaskMethodComboBox;
  private JPanel myPanel;
  private JRadioButton myXmlRadioButton;
  private JRadioButton myTextRadioButton;
  private JButton myTest2Button;
  private JRadioButton myJsonRadioButton;
  private JButton myManageTemplateVariablesButton;
  private JButton myResetToDefaultsButton;
  private JPanel myCardPanel;
  private JBLabel mySingleTaskURLLabel;
  private JBCheckBox myDownloadTasksInSeparateRequests;

  private Map<JTextField, TemplateVariable> myField2Variable;
  private Map<JRadioButton, ResponseType> myRadio2ResponseType;

  public GenericRepositoryEditor(final Project project,
                                 final T repository,
                                 final Consumer<T> changeListener) {
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
    myUseHttpAuthenticationCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        loginUrlEnablingChanged();
      }
    });

    ActionListener radioButtonListener = new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        singleTaskUrlEnablingChanged();
        doApply();
        selectCardByResponseType();
      }
    };
    myXmlRadioButton.addActionListener(radioButtonListener);
    myTextRadioButton.addActionListener(radioButtonListener);
    myJsonRadioButton.addActionListener(radioButtonListener);

    myLoginMethodTypeComboBox.setSelectedItem(myRepository.getLoginMethodType().toString());
    myTasksListMethodTypeComboBox.setSelectedItem(myRepository.getTasksListMethodType().toString());
    mySingleTaskMethodComboBox.setSelectedItem(myRepository.getSingleTaskMethodType().toString());

    // set default listener updating model fields
    installListener(myLoginMethodTypeComboBox);
    installListener(myTasksListMethodTypeComboBox);
    installListener(mySingleTaskMethodComboBox);
    installListener(myLoginURLText);
    installListener(myTasksListURLText);
    installListener(mySingleTaskURLText);
    installListener(myDownloadTasksInSeparateRequests);
    myTabbedPane.addTab("Server Configuration", myPanel);

    // Put appropriate configuration components on the card panel
    ResponseHandler xmlHandler = myRepository.getResponseHandler(ResponseType.XML);
    ResponseHandler jsonHandler = myRepository.getResponseHandler(ResponseType.JSON);
    ResponseHandler textHandler = myRepository.getResponseHandler(ResponseType.TEXT);
    // Select appropriate card pane
    myCardPanel.add(xmlHandler.getConfigurationComponent(myProject), ResponseType.XML.getMimeType());
    myCardPanel.add(jsonHandler.getConfigurationComponent(myProject), ResponseType.JSON.getMimeType());
    myCardPanel.add(textHandler.getConfigurationComponent(myProject), ResponseType.TEXT.getMimeType());

    myRadio2ResponseType = new IdentityHashMap<>();
    myRadio2ResponseType.put(myJsonRadioButton, ResponseType.JSON);
    myRadio2ResponseType.put(myXmlRadioButton, ResponseType.XML);
    myRadio2ResponseType.put(myTextRadioButton, ResponseType.TEXT);

    myManageTemplateVariablesButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        final ManageTemplateVariablesDialog dialog = new ManageTemplateVariablesDialog(myManageTemplateVariablesButton);
        dialog.setTemplateVariables(myRepository.getAllTemplateVariables());
        if (dialog.showAndGet()) {
          myRepository.setTemplateVariables(ContainerUtil.filter(dialog.getTemplateVariables(), variable -> !variable.isReadOnly()));
          myCustomPanel.removeAll();
          myCustomPanel.add(createCustomPanel());
          //myCustomPanel.repaint();
          myTabbedPane.getComponentAt(0).repaint();

          //myLoginURLText = createEditorFieldWithPlaceholderCompletion(myRepository.getLoginUrl());
          List<String> placeholders = createPlaceholdersList(myRepository);
          ((TextFieldWithAutoCompletion)myLoginURLText).setVariants(placeholders);
          ((TextFieldWithAutoCompletion)myTasksListURLText).setVariants(concat(placeholders, "{max}", "{since}"));
          ((TextFieldWithAutoCompletion)mySingleTaskURLText).setVariants(concat(placeholders, "{id}"));
          myPanel.repaint();
        }
      }
    });

    myResetToDefaultsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        myRepository.resetToDefaults();
        // TODO: look closely
        reset(myRepository.clone());
      }
    });

    selectRadioButtonByResponseType();
    selectCardByResponseType();
    loginUrlEnablingChanged();
    singleTaskUrlEnablingChanged();
    myDownloadTasksInSeparateRequests.setSelected(myRepository.getDownloadTasksInSeparateRequests());
  }

  private void singleTaskUrlEnablingChanged() {
    boolean enabled = !myTextRadioButton.isSelected();
    // single task URL doesn't make sense when legacy regex handler is used
    mySingleTaskURLText.setEnabled(enabled);
    mySingleTaskMethodComboBox.setEnabled(enabled);
    mySingleTaskURLLabel.setEnabled(enabled);
  }

  protected void loginUrlEnablingChanged() {
    boolean enabled = !myLoginAnonymouslyJBCheckBox.isSelected() && !myUseHttpAuthenticationCheckBox.isSelected();
    myLoginURLLabel.setEnabled(enabled);
    myLoginURLText.setEnabled(enabled);
    myLoginMethodTypeComboBox.setEnabled(enabled);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myField2Variable = new IdentityHashMap<>();
    FormBuilder builder = FormBuilder.createFormBuilder();
    for (final TemplateVariable variable : myRepository.getTemplateVariables()) {
      if (variable.isShownOnFirstTab()) {
        JTextField field = variable.isHidden() ? new JPasswordField(variable.getValue()) : new JTextField(variable.getValue());
        myField2Variable.put(field, variable);
        installListener(field);
        JBLabel label = new JBLabel(prettifyVariableName(variable.getName()) + ":", SwingConstants.RIGHT);
        label.setAnchor(getAnchor());
        builder.addLabeledComponent(label, field);
      }
    }
    return builder.getPanel();
  }

  protected void reset(final GenericRepository clone) {
    myLoginURLText.setText(clone.getLoginUrl());
    myTasksListURLText.setText(clone.getTasksListUrl());
    mySingleTaskURLText.setText(clone.getSingleTaskUrl());
    //myTaskPatternText.setText(clone.getTaskPattern());
    myLoginMethodTypeComboBox.setSelectedItem(clone.getLoginMethodType());
    myTasksListMethodTypeComboBox.setSelectedItem(clone.getTasksListMethodType());
    mySingleTaskMethodComboBox.setSelectedItem(clone.getSingleTaskMethodType());
    selectRadioButtonByResponseType();
    selectCardByResponseType();
    loginUrlEnablingChanged();
    myDownloadTasksInSeparateRequests.setSelected(myRepository.getDownloadTasksInSeparateRequests());
  }

  private void selectRadioButtonByResponseType() {
    for (Map.Entry<JRadioButton, ResponseType> entry : myRadio2ResponseType.entrySet()) {
      if (entry.getValue() == myRepository.getResponseType()) {
        entry.getKey().setSelected(true);
      }
    }
  }

  private void selectCardByResponseType() {
    CardLayout cardLayout = (CardLayout) myCardPanel.getLayout();
    cardLayout.show(myCardPanel, myRepository.getResponseType().getMimeType());
  }

  @Override
  public void apply() {
    myRepository.setLoginUrl(myLoginURLText.getText());
    myRepository.setTasksListUrl(myTasksListURLText.getText());
    myRepository.setSingleTaskUrl(mySingleTaskURLText.getText());

    myRepository.setLoginMethodType(HTTPMethod.valueOf((String)myLoginMethodTypeComboBox.getSelectedItem()));
    myRepository.setTasksListMethodType(HTTPMethod.valueOf((String)myTasksListMethodTypeComboBox.getSelectedItem()));
    myRepository.setSingleTaskMethodType(HTTPMethod.valueOf((String)mySingleTaskMethodComboBox.getSelectedItem()));

    myRepository.setDownloadTasksInSeparateRequests(myDownloadTasksInSeparateRequests.isSelected());
   for (Map.Entry<JTextField, TemplateVariable> entry : myField2Variable.entrySet()) {
      TemplateVariable variable = entry.getValue();
      JTextField field = entry.getKey();
      variable.setValue(field.getText());
    }
    for (Map.Entry<JRadioButton, ResponseType> entry : myRadio2ResponseType.entrySet()) {
      if (entry.getKey().isSelected()) {
        myRepository.setResponseType(entry.getValue());
      }
    }
    super.apply();
  }

  private void createUIComponents() {
    List<String> placeholders = createPlaceholdersList(myRepository);
    myLoginURLText = createTextFieldWithCompletion(myRepository.getLoginUrl(), placeholders);
    myTasksListURLText = createTextFieldWithCompletion(myRepository.getTasksListUrl(), concat(placeholders, "{max}", "{since}"));
    mySingleTaskURLText = createTextFieldWithCompletion(myRepository.getSingleTaskUrl(), concat(placeholders, "{id}"));
  }

  private TextFieldWithAutoCompletion<String> createTextFieldWithCompletion(String text, final List<String> variants) {
    final StringsCompletionProvider provider = new StringsCompletionProvider(variants, null) {
      @Nullable
      @Override
      public String getPrefix(@NotNull String text, int offset) {
        final int i = text.lastIndexOf('{', offset - 1);
        if (i < 0) {
          return "";
        }
        return text.substring(i, offset);
      }
    };
    return new TextFieldWithAutoCompletion<>(myProject, provider, true, text);
  }

  @Override
  public void setAnchor(@Nullable JComponent anchor) {
    super.setAnchor(anchor);
    List<JBLabel> labels = UIUtil.findComponentsOfType(myCustomPanel, JBLabel.class);
    for (JBLabel label : labels) {
      label.setAnchor(anchor);
    }
  }
}
