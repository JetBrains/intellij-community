package com.intellij.openapi.ui.ex;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.ui.UIBundle;
import com.intellij.CommonBundle;

import javax.swing.*;

public class MessagesEx extends Messages {

  public static MessageInfo fileIsReadOnly(Project project, String filePath) {
    return error(project, UIBundle.message("file.is.read.only.message.text", filePath));
  }

  public static MessageInfo filesAreReadOnly(Project project, String[] files) {
    if (files.length == 1){
      return fileIsReadOnly(project, files[0]);
    } else {
      return error(project, UIBundle.message("files.are.read.only.message.text", filePaths(files)));
    }
  }

  private static String filePaths(String[] files) {
    return StringUtil.join(files, ",\n");
  }

  public static MessageInfo fileIsReadOnly(Project project, VirtualFile file) {
    return fileIsReadOnly(project, file.getPresentableUrl());
  }

  public static MessageInfo error(Project project, String message) {
    return error(project, message, UIBundle.message("error.dialog.title"));
  }

  public static MessageInfo error(Project project, String message, String title) {
    return new MessageInfo(project, message, title);
  }

  public static abstract class BaseDialogInfo<ThisClass extends BaseDialogInfo> {
    private Project myProject;
    private String myMessage;
    private String myTitle;
    private Icon myIcon;
    private String[] myOptions = new String[]{CommonBundle.getOkButtonText()};
    private int myDefaultOption = 0;

    protected BaseDialogInfo(Project project) {
      myProject = project;
    }

    public BaseDialogInfo(Project project, String message, String title, Icon icon) {
      this(project);
      myMessage = message;
      myTitle = title;
      myIcon = icon;
    }

    public ThisClass setTitle(String title) { myTitle = title; return getThis(); }

    public String getMessage() { return myMessage; }

    public ThisClass appendMessage(String message) {
      myMessage += message;
      return getThis();
    }

    public void setOptions(String[] options, int defaultOption) {
      myOptions = options;
      myDefaultOption = defaultOption;
    }

    protected abstract ThisClass getThis();

    public ThisClass setIcon(Icon icon) { myIcon = icon; return getThis(); }

    public void setMessage(String message) {
      myMessage = message;
    }

    public Project getProject() {
      return myProject;
    }

    public String getTitle() {
      return myTitle;
    }

    public String[] getOptions() {
      return myOptions;
    }

    public int getDefaultOption() {
      return myDefaultOption;
    }

    public Icon getIcon() {
      return myIcon;
    }
  }

  public static class MessageInfo extends BaseDialogInfo<MessageInfo> {
    public MessageInfo(Project project, String message, String title) {
      super(project, message, title, getErrorIcon());
    }

    public int showNow() {
      return showDialog(getProject(), getMessage(), getTitle(), getOptions(), getDefaultOption(), getIcon());
    }

    public void showLater() {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            showNow();
          }
        });
    }

    public int askYesNo() {
      setIcon(getQuestionIcon());
      return showYesNoDialog(getProject(), getMessage(), getTitle(), getIcon());
    }

    public int ask(String[] options, int defaultOptionIndex) {
      setOptions(options, defaultOptionIndex);
      return showNow();
    }

    protected MessageInfo getThis() {
      return this;
    }
  }

  public static class ChoiceInfo extends BaseInputInfo<ChoiceInfo> {
    private String[] myChoises = ArrayUtil.EMPTY_STRING_ARRAY;
    private String myDefaultChoice = null;
    private boolean myEditable = false;

    public ChoiceInfo(Project project) {
      super(project);
      setIcon(getQuestionIcon());
      setOptions(new String[]{CommonBundle.getOkButtonText()}, 0);
    }

    public ChoiceInfo getThis() {
      return this;
    }

    public ChoiceInfo setChoices(String[] choices, int defaultChoiceIndex) {
      setChoices(choices, defaultChoiceIndex >= 0 ? choices[defaultChoiceIndex] : null);
      return getThis();
    }

    public ChoiceInfo setChoices(String[] choices, String defaultChoice) {
      myChoises = choices;
      myDefaultChoice = defaultChoice;
      return getThis();
    }

    public UserInput askUser() {
      ChooseDialog dialog = new ChooseDialog(getProject(), getMessage(), getTitle(), getIcon(), myChoises, myDefaultChoice, getOptions(), getDefaultOption());
      dialog.setValidator(null);
      JComboBox comboBox = dialog.getComboBox();
      comboBox.setEditable(myEditable);
      if (myEditable)
        comboBox.getEditor().setItem(myDefaultChoice);
      comboBox.setSelectedItem(myDefaultChoice);
      dialog.show();
      Object selectedItem = comboBox.getSelectedItem();
      return new UserInput(selectedItem != null ? selectedItem.toString() : null, dialog.getExitCode());
    }
  }

  public static class UserInput {
    private final int mySelectedOption;
    private final String myInput;

    public UserInput(String choice, int option) {
      mySelectedOption = option;
      myInput = choice;
    }

    public String getInput() {
      return myInput;
    }

    public int getSelectedOption() {
      return mySelectedOption;
    }
  }

  public static class InputInfo extends BaseInputInfo<InputInfo> {
    public InputInfo(Project project) {
      super(project);
      setOptions(new String[]{CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText()}, 0);
    }

    public UserInput askUser() {
      InputDialog dialog = new InputDialog(getProject(), getMessage(), getTitle(), getIcon(), null, null, getOptions(), getDefaultOption());
      dialog.show();
      return new UserInput(dialog.getTextField().getText(), dialog.getExitCode());
    }

    public InputInfo getThis() {
      return this;
    }
  }

  public static abstract class BaseInputInfo<ThisClass extends BaseInputInfo> extends BaseDialogInfo<ThisClass> {
    public BaseInputInfo(Project project) {
      super(project);
    }

    public String forceUserInput() {
      setOptions(new String[]{CommonBundle.getOkButtonText()}, 0);
      return askUser().getInput();
    }

    public abstract UserInput askUser();
  }
}
