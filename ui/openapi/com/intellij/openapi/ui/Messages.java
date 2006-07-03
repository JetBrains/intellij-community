/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.InsertPathAction;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class Messages {
  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;

  private static Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Messages");
  protected static final String OK_BUTTON = CommonBundle.getOkButtonText();
  protected static final String YES_BUTTON = CommonBundle.getYesButtonText();
  protected static final String NO_BUTTON = CommonBundle.getNoButtonText();
  protected static final String CANCEL_BUTTON = CommonBundle.getCancelButtonText();

  public static TestDialog setTestDialog(TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This methos is available for tests only");
    }
    TestDialog oldValue = ourTestImplementation;
    ourTestImplementation = newValue;
    return oldValue;
  }

  public static Icon getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  public static Icon getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  public static Icon getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  public static Icon getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  public static int showDialog(Project project, String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ourTestImplementation.show(message);
    }
    else {
      MessageDialog dialog = new MessageDialog(project, message, title, options, defaultOptionIndex, icon);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  public static int showDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ourTestImplementation.show(message);
    }
    else {
      MessageDialog dialog = new MessageDialog(parent, message, title, options, defaultOptionIndex, icon);
      dialog.show();
      return dialog.getExitCode();
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message, String title, String[] options, int defaultOptionIndex, Icon icon) {

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ourTestImplementation.show(message);
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode()) throw new RuntimeException(message);
      MessageDialog dialog = new MessageDialog(message, title, options, defaultOptionIndex, icon);
      dialog.show();
      return dialog.getExitCode();
    }


  }

  /**
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project,boolean)
   */
  public static void showMessageDialog(Project project, String message, String title, Icon icon) {
    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  public static void showMessageDialog(Component parent, String message, String title, Icon icon) {
    showDialog(parent, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Icon)
   * @see #showMessageDialog(Component, String, String, Icon)
   */
  public static void showMessageDialog(String message, String title, Icon icon) {
    showDialog(message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   */
  public static int showYesNoDialog(Project project, String message, String title, Icon icon) {
    return showDialog(project, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon);
  }

  /**
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   */
  public static int showYesNoDialog(Component parent, String message, String title, Icon icon) {
    return showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return <code>0</code> if user pressed "Yes" and returns <code>1</code> if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  public static int showYesNoDialog(String message, String title, Icon icon) {
    return showDialog(message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon);
  }

  public static int showOkCancelDialog(Project project, String message, String title, Icon icon) {
    return showDialog(project, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  public static int showOkCancelDialog(Component parent, String message, String title, Icon icon) {
    return showDialog(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showOkCancelDialog(Project, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, Icon)
   */
  public static int showOkCancelDialog(String message, String title, Icon icon) {
    return showDialog(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  public static void showErrorDialog(Project project, String message, String title) {
    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(Component component, String message, String title) {
    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(Component component, String message) {
    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(String message, String title) {
    showDialog(message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showWarningDialog(Project project, String message, String title) {
    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static void showWarningDialog(Component component, String message, String title) {
    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(String message, String title) {
    showDialog(message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static int showYesNoCancelDialog(Project project, String message, String title, Icon icon) {
    return showDialog(project, message, title, new String[]{YES_BUTTON, NO_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  public static int showYesNoCancelDialog(Component parent, String message, String title, Icon icon) {
    return showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showYesNoCancelDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  public static int showYesNoCancelDialog(String message, String title, Icon icon) {
    return showDialog(message, title, new String[]{YES_BUTTON, NO_BUTTON, CANCEL_BUTTON}, 0, icon);
  }

  /**
   * @return trimmed inpit string or <code>null</code> if user cancelled dialog.
   */
  public static String showInputDialog(Project project, String message, String title, Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed inpit string or <code>null</code> if user cancelled dialog.
   */
  public static String showInputDialog(Component parent, String message, String title, Icon icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon)
   * @see #showInputDialog(Component, String, String, Icon)
   */
  public static String showInputDialog(String message, String title, Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  public static String showInputDialog(Project project,
                                       String message,
                                       String title,
                                       Icon icon,
                                       String initialValue,
                                       InputValidator validator) {
    InputDialog dialog = new InputDialog(project, message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  public static String showInputDialog(Component parent,
                                       String message,
                                       String title,
                                       Icon icon,
                                       String initialValue,
                                       InputValidator validator) {
    InputDialog dialog = new InputDialog(parent, message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  public static String showInputDialog(String message, String title, Icon icon, String initialValue, InputValidator validator) {
    InputDialog dialog = new InputDialog(message, title, icon, initialValue, validator);
    dialog.show();
    return dialog.getInputString();
  }

  public static String showEditableChooseDialog(String message,
                                                String title,
                                                Icon icon,
                                                String[] values,
                                                String initialValue,
                                                InputValidator validator) {
    ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
    dialog.setValidator(validator);
    dialog.getComboBox().setEditable(true);
    dialog.getComboBox().getEditor().setItem(initialValue);
    dialog.getComboBox().setSelectedItem(initialValue);
    dialog.show();
    return dialog.getInputString();
  }

  public static int showChooseDialog(String message, String title, String[] values, String initialValue, Icon icon) {
    ChooseDialog dialog = new ChooseDialog(message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  public static int showChooseDialog(Component parent, String message, String title, String[] values, String initialValue, Icon icon) {
    ChooseDialog dialog = new ChooseDialog(parent, message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  /**
   * @see com.intellij.openapi.ui.DialogWrapper#DialogWrapper(Project,boolean)
   */
  public static int showChooseDialog(Project project, String message, String title, Icon icon, String[] values, String initialValue) {
    ChooseDialog dialog = new ChooseDialog(project, message, title, icon, values, initialValue);
    dialog.show();
    return dialog.getSelectedIndex();
  }

  /**
   * Shows dialog with given message and title, infomation icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(Component component, String message, String title) {
    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, infomation icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(Project project, String message, String title) {
    showMessageDialog(project, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, infomation icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(String message, String title) {
    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field 
   */
  public static void showTextAreaDialog(final JTextField textField, final String title, @NonNls final String dimensionServiceKey) {
    JTextArea textArea = new JTextArea(10, 50);
    textArea.setWrapStyleWord(true);
    textArea.setLineWrap(true);
    textArea.setDocument(textField.getDocument());
    InsertPathAction.copyFromTo(textField, textArea);
    DialogBuilder builder = new DialogBuilder(textField);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(textArea);
    builder.setDimensionServiceKey(dimensionServiceKey);
    builder.setCenterPanel(scrollPane);
    builder.setPreferedFocusComponent(textArea);
    String rawText = title;
    if (StringUtil.endsWithChar(rawText, ':')) {
      rawText = rawText.substring(0, rawText.length() - 1);
    }
    builder.setTitle(rawText);
    builder.addCloseButton();
    builder.show();
  }

  private static class MessageDialog extends DialogWrapper {
    protected String myMessage;
    protected String[] myOptions;
    protected int myDefaultOptionIndex;
    protected Icon myIcon;

    public MessageDialog(Project project, String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
      super(project, false);
      _init(title, message, options, defaultOptionIndex, icon);
    }

    public MessageDialog(Component parent, String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
      super(parent, false);
      _init(title, message, options, defaultOptionIndex, icon);
    }

    public MessageDialog(String message, String title, String[] options, int defaultOptionIndex, Icon icon) {
      super(false);
      _init(title, message, options, defaultOptionIndex, icon);
    }

    private void _init(String title, String message, String[] options, int defaultOptionIndex, Icon icon) {
      setTitle(title);
      myMessage = message;
      myOptions = options;
      myDefaultOptionIndex = defaultOptionIndex;
      myIcon = icon;
      setButtonsAlignment(SwingUtilities.CENTER);
      init();
    }

    protected Action[] createActions() {
      Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        actions[i] = new AbstractAction(option) {
          public void actionPerformed(ActionEvent e) {
            close(exitCode);
          }
        };
        if (i == myDefaultOptionIndex) {
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
        }
        assignMnemonic(option, actions[i]);

      }
      return actions;
    }

    private void assignMnemonic(String option, Action action) {
      int mnemoPos = option.indexOf("&");
      if (mnemoPos >= 0 && mnemoPos < option.length() - 2) {
        String mnemoChar = option.substring(mnemoPos + 1, mnemoPos + 2).trim();
        if (mnemoChar.length() == 1) {
          action.putValue(Action.MNEMONIC_KEY, new Integer(mnemoChar.charAt(0)));
        }
      }
    }

    public void doCancelAction() {
      close(-1);
    }

    protected JComponent createNorthPanel() {
      return null;
    }

    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      if (myMessage != null) {
        JTextPane messagePane = new JTextPane();
        messagePane.setContentType("text/html");
        messagePane.setBackground(panel.getBackground());
        messagePane.setText(myMessage);
        messagePane.setEditable(false);
        messagePane.setCaretPosition(0);
        messagePane.setFont(new JLabel().getFont());
        final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        final Dimension textSize = messagePane.getPreferredSize();
        if (textSize.width > screenSize.width *4/5 || textSize.height > screenSize.height / 2) {

          final JScrollPane pane = ScrollPaneFactory.createScrollPane(messagePane);
          pane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
          pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
          pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

          final Dimension preferredSize = new Dimension(Math.min(textSize.width, screenSize.width * 4 / 5), Math.min(textSize.height, screenSize.height/2));
          pane.setPreferredSize(preferredSize);
          panel.add(pane, BorderLayout.CENTER);
        }
        else {
          panel.add(messagePane, BorderLayout.CENTER);
        }
      }
      return panel;
    }

  }

  protected static class InputDialog extends MessageDialog {
    private JTextField myField;
    private InputValidator myValidator;

    public InputDialog(Project project,
                       String message,
                       String title,
                       Icon icon,
                       String initialValue,
                       InputValidator validator,
                       String[] options,
                       int defaultOption) {
      super(project, message, title, options, defaultOption, icon);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(Project project, String message, String title, Icon icon, String initialValue, InputValidator validator) {
      this(project, message, title, icon, initialValue, validator, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public InputDialog(Component parent, String message, String title, Icon icon, String initialValue, InputValidator validator) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myValidator = validator;
      myField.setText(initialValue);
    }

    public InputDialog(String message, String title, Icon icon, String initialValue, InputValidator validator) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myValidator = validator;
      myField.setText(initialValue);
    }

    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == 0) { // "OK" is default button. It has index 0.
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              String inputString = myField.getText().trim();
              if (
                myValidator == null ||
                myValidator.checkInput(inputString) &&
                myValidator.canClose(inputString)
              ) {
                close(exitCode);
              }
            }
          };
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myField.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(DocumentEvent event) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myField.getText().trim()));
            }
          });
        }
        else {
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    protected JComponent createCenterPanel() {
      return null;
    }

    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      myField = new JTextField(30);
      messagePanel.add(myField, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);

      return panel;
    }

    public JTextField getTextField() {
      return myField;
    }

    public JComponent getPreferredFocusedComponent() {
      return myField;
    }

    public String getInputString() {
      if (getExitCode() == 0) {
        return myField.getText().trim();
      }
      else {
        return null;
      }
    }
  }

  protected static class ChooseDialog extends MessageDialog {
    private ComboBox myComboBox;
    private InputValidator myValidator;

    public ChooseDialog(Project project,
                        String message,
                        String title,
                        Icon icon,
                        String[] values,
                        String initialValue,
                        String[] options,
                        int defaultOption) {
      super(project, message, title, options, defaultOption, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(Project project, String message, String title, Icon icon, String[] values, String initialValue) {
      this(project, message, title, icon, values, initialValue, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0);
    }

    public ChooseDialog(Component parent, String message, String title, Icon icon, String[] values, String initialValue) {
      super(parent, message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    public ChooseDialog(String message, String title, Icon icon, String[] values, String initialValue) {
      super(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, 0, icon);
      myComboBox.setModel(new DefaultComboBoxModel(values));
      myComboBox.setSelectedItem(initialValue);
    }

    protected Action[] createActions() {
      final Action[] actions = new Action[myOptions.length];
      for (int i = 0; i < myOptions.length; i++) {
        String option = myOptions[i];
        final int exitCode = i;
        if (i == myDefaultOptionIndex) {
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              if (myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim())) {
                close(exitCode);
              }
            }
          };
          actions[i].putValue(DEFAULT_ACTION, Boolean.TRUE);
          myComboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(myComboBox.getSelectedItem().toString().trim()));
            }
          });
          final JTextField textField = (JTextField)myComboBox.getEditor().getEditorComponent();
          textField.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(DocumentEvent event) {
              actions[exitCode].setEnabled(myValidator == null || myValidator.checkInput(textField.getText().trim()));
            }
          });
        }
        else { // "Cancel" action
          actions[i] = new AbstractAction(option) {
            public void actionPerformed(ActionEvent e) {
              close(exitCode);
            }
          };
        }
      }
      return actions;
    }

    protected JComponent createCenterPanel() {
      return null;
    }

    protected JComponent createNorthPanel() {
      JPanel panel = new JPanel(new BorderLayout(15, 0));
      if (myIcon != null) {
        JLabel iconLabel = new JLabel(myIcon);
        Container container = new Container();
        container.setLayout(new BorderLayout());
        container.add(iconLabel, BorderLayout.NORTH);
        panel.add(container, BorderLayout.WEST);
      }

      JPanel messagePanel = new JPanel(new BorderLayout());
      if (myMessage != null) {
        JLabel textLabel = new JLabel(myMessage);
        textLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
        textLabel.setUI(new MultiLineLabelUI());
        messagePanel.add(textLabel, BorderLayout.NORTH);
      }

      myComboBox = new ComboBox(220);
      messagePanel.add(myComboBox, BorderLayout.SOUTH);
      panel.add(messagePanel, BorderLayout.CENTER);
      return panel;
    }

    protected void doOKAction() {
      String inputString = myComboBox.getSelectedItem().toString().trim();
      if (myValidator == null ||
          myValidator.checkInput(inputString) &&
          myValidator.canClose(inputString)) {
        super.doOKAction();
      }
    }

    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    public String getInputString() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedItem().toString();
      }
      else {
        return null;
      }
    }

    public int getSelectedIndex() {
      if (getExitCode() == 0) {
        return myComboBox.getSelectedIndex();
      }
      else {
        return -1;
      }
    }

    public JComboBox getComboBox() {
      return myComboBox;
    }

    public void setValidator(InputValidator validator) {
      myValidator = validator;
    }
  }
}
